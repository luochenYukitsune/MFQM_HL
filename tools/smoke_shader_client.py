"""Two focused grounded views with real Iris/Sodium/FirstPersonModel in a workspace-only instance.

The supplied shader ZIPs/settings and user mods are read and copied, never edited.
A vanilla fallback is a failure, not a successful compatibility result.
"""
import argparse
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
from PIL import Image

from smoke_installed_client import ROOT,instance_lock,java_argument,prepare_fml_config,sha256,validate_runtime,workspace_path
from smoke_viscosity_client import viscosity_vm,upstream_skin_pack_metadata_error

DEFAULT_INSTANCE=Path('D:/mc/mc/.minecraft/versions/1.21.11-NeoForge_21.11.45')
COMPAT=('iris-neoforge-1.10.8+mc1.21.11.jar','sodium-neoforge-0.8.14+mc1.21.11.jar',
        'firstperson-neoforge-2.7.3-mc1.21.11.jar','skinlayers3d-neoforge-1.11.3-mc1.21.11.jar')

def find_mod(mods,name):
    files=[p for p in mods.glob('*.jar') if p.name.endswith(name)]
    if len(files)!=1:raise ValueError(f'Expected exactly one compatible {name}, found {len(files)}')
    return files[0]

def prepare(game,pack,instance,smoke_only=False):
    runtime=json.loads((ROOT/'build/installed-client-runtime.json').read_text(encoding='utf-8'))
    classpath,vm,program=validate_runtime(runtime,ROOT)
    if '--quickPlaySingleplayer' not in program or 'mfqm-port-checks' not in program:
        raise ValueError('Prepare the copied-world launcher with prepareInstalledClient -PmfqmClientChecks before running shader checks')
    jar=workspace_path(Path(runtime['jar'])); digest=sha256(jar)
    game.mkdir(parents=True,exist_ok=True)
    previous=workspace_path(game/'previous-runs'/str(time.time_ns()))
    for relative in ('mods','saves','screenshots'):
        path=workspace_path(game/relative)
        if path.exists():
            previous.mkdir(parents=True,exist_ok=True);shutil.move(str(path),str(previous/relative))
    mods=workspace_path(game/'mods'); mods.mkdir()
    shutil.copy2(jar,mods/jar.name)
    artifacts={jar.name:digest}
    for name in COMPAT:
        original=find_mod(instance/'mods',name);target=mods/name;shutil.copy2(original,target)
        artifacts[name]=sha256(original)
        if sha256(target)!=artifacts[name]:raise ValueError('Copied mod digest mismatch')
    shaders=workspace_path(game/'shaderpacks');shaders.mkdir(exist_ok=True)
    shutil.copy2(pack,shaders/pack.name)
    if sha256(pack)!=sha256(shaders/pack.name):raise ValueError('Copied shader digest mismatch')
    settings=pack.with_name(pack.name+'.txt')
    if settings.exists():shutil.copy2(settings,shaders/settings.name)
    config=workspace_path(game/'config');config.mkdir(exist_ok=True)
    prepare_fml_config(config/'fml.toml')
    # Match normal launcher defaults. Iris 1.10.8 casts the device to GlDevice and
    # cannot use NeoForge's development-only ValidationGpuDevice wrapper.
    neoforge=config/'neoforge-client.toml'
    nf=neoforge.read_text(encoding='utf-8-sig') if neoforge.exists() else ''
    nf=re.sub(r'(?m)^enableB3DValidationLayer\s*=.*\n?','',nf)
    neoforge.write_text(nf+'\nenableB3DValidationLayer = false\n',encoding='utf-8')
    (config/'iris.properties').write_text('enableShaders=true\nshaderPack='+pack.name+'\nallowUnknownShaders=false\ndisableUpdateMessage=true\n',encoding='utf-8')
    source=ROOT/'run/1.21.11/server/smoke-world'
    if not (source/'level.dat').exists():raise ValueError('Missing isolated world template')
    shutil.copytree(source,workspace_path(game/'saves/mfqm-port-checks'))
    (game/'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\nlang:zh_cn\nrenderDistance:6\nsimulationDistance:5\nmipmapLevels:0\nmaxFps:60\n',encoding='utf-8')
    vm=viscosity_vm(vm,True,first_person=True,skin_layers=True)
    vm='\n'.join(line for line in vm.splitlines() if not line.startswith(('-Dmfqm.viscosityChecks=','-Dmfqm.shaderChecks=','-Dmfqm.shaderExpected=')))
    args=vm+'\n-Dmfqm.viscosityChecks=false\n-Dmfqm.shaderChecks=true\n'+java_argument('-Dmfqm.shaderExpected='+pack.name)
    args+='\n-Dmfqm.shaderSmoke='+str(smoke_only).lower()
    args+='\n-Dfile.encoding=UTF-8\n-Xmx4G\n-cp\n'+java_argument(os.pathsep.join(str(p) for p in classpath))+'\n'+program+'\n--width\n960\n--height\n540\n'
    launch=workspace_path(game/'shader-client-java.args');launch.write_text(args,encoding='utf-8')
    return runtime,launch,artifacts

def run(pack,instance,timeout,smoke_only=False):
    safe=re.sub(r'[^a-zA-Z0-9.-]','_',pack.stem)
    game=workspace_path(ROOT/'run/1.21.11/shader-client'/safe)
    log=workspace_path(ROOT/'run/1.21.11/validation'/('shader-'+safe+'.log'));log.parent.mkdir(parents=True,exist_ok=True)
    result={'pack':pack.name,'shader_sha256':sha256(pack),'game':str(game),'log':str(log),'scope':'smoke' if smoke_only else 'focused','passed':False}
    process=None;captured=[]
    with instance_lock(game):
        try:
            runtime,launch,artifacts=prepare(game,pack,instance,smoke_only);result['mod_sha256']=artifacts
            environment=os.environ.copy()
            for key in list(environment):
                if key.upper() in ('FML_MOD_CLASSES','MOD_CLASSES','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH'):del environment[key]
            process=subprocess.Popen([runtime['javaExecutable'],'@'+str(launch)],cwd=game,env=environment,
                stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
            output=queue.Queue()
            def reader():
                for line in process.stdout:output.put(line)
                output.put(None)
            threading.Thread(target=reader,daemon=True).start();deadline=time.monotonic()+timeout
            with log.open('w',encoding='utf-8') as f:
                f.write(json.dumps(result,ensure_ascii=False)+'\n')
                while time.monotonic()<deadline:
                    try:line=output.get(timeout=1)
                    except queue.Empty:continue
                    if line is None:break
                    captured.append(line);f.write(line);f.flush()
                    if any(mark in line for mark in ('MFQM_SHADER','MFQM_CLIENT_CHECKS_FAILED','Creating pipeline','Using shaderpack','Failed to load shaderpack','OpenGL Renderer')):print(line.rstrip(),flush=True)
                else:raise TimeoutError('Shader check exceeded '+str(timeout)+' seconds')
            code=process.wait(timeout=15);text=''.join(captured)
            markers=('MFQM_SHADER_SMOKE_COMPLETE',) if smoke_only else ('MFQM_SHADER_CHECKS_COMPLETE','MFQM_SHADER_FIRSTPERSON_VERIFIED',
                           'MFQM_SHADER_VISUAL_SAVED file=mfqm-shader-body.png','MFQM_SHADER_VISUAL_SAVED file=mfqm-shader-firstperson.png')
            for marker in ('MFQM_INSTALLED_JAR_LOADED','MFQM_CLIENT_CHECKS_COMPLETE',*markers):
                if marker not in text:raise RuntimeError('Missing '+marker)
            unexpected=[line.strip() for line in captured if re.search(r'(?:type=ERROR|severity=HIGH|/ERROR\]|\bmain ERROR\b)',line)
                        and not upstream_skin_pack_metadata_error(line,True,True)]
            if code!=0 or unexpected:raise RuntimeError('Unexpected game/render error: '+'; '.join(unexpected[:3]))
            if not smoke_only:
                evidence=[game/'screenshots'/name for name in ('mfqm-shader-body.png','mfqm-shader-firstperson.png','mfqm-shader-pools.png')]
                for picture in evidence:
                    with Image.open(picture) as image:image.verify()
                result['evidence']=[str(p) for p in evidence]
            result['passed']=True
        except Exception as failure:
            result['error']=str(failure);print('FAILED '+pack.name+': '+str(failure),flush=True)
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
    print(json.dumps(result,ensure_ascii=False),flush=True)
    return result

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance',type=Path,default=DEFAULT_INSTANCE)
    parser.add_argument('--pack',action='append',help='Exact ZIP filename; may be repeated')
    parser.add_argument('--all',action='store_true')
    parser.add_argument('--smoke-only',action='store_true',help='Compile/load/render only; no screenshots or extended strand/FPM checks')
    parser.add_argument('--timeout',type=int,default=240)
    args=parser.parse_args()
    if args.all==bool(args.pack):parser.error('Choose --pack or --all')
    shader_dir=args.instance/'shaderpacks'
    packs=[shader_dir/name for name in args.pack] if args.pack else sorted(shader_dir.glob('*.zip'))
    output=ROOT/'run/1.21.11/validation/shader-results.json'
    results=json.loads(output.read_text(encoding='utf-8')) if output.exists() else []
    new_results=[]
    for pack in packs:
        if not pack.is_file() or pack.parent.resolve()!=shader_dir.resolve():raise ValueError('Shader pack must be an existing ZIP in the shader directory')
        result=run(pack,args.instance,args.timeout,args.smoke_only);new_results.append(result)
        results=[r for r in results if r['pack']!=pack.name]+[result]
        output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    raise SystemExit(0 if all(r['passed'] for r in new_results) else 1)
