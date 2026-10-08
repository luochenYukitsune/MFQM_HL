package com.mfqm.morefunquicksandmod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Resource and actual language-manager checks in the isolated packaged client. */
final class ChineseLanguageChecks {
    static void run(Minecraft game) throws java.io.IOException {
        var container=net.neoforged.fml.ModList.get().getModContainerById(MFQM.MOD_ID).orElseThrow();
        var factory=container.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).orElseThrow();
        require(factory.createScreen(container,game.screen)!=null,"configuration screen factory");
        JsonObject english=read(game,"en_us"),chinese=read(game,"zh_cn");
        for(var entry:english.entrySet()) {
            require(chinese.has(entry.getKey()),"Chinese translation missing: "+entry.getKey());
            require(formats(entry.getValue().getAsString()).equals(formats(chinese.get(entry.getKey()).getAsString())),
                    "format arguments mismatch: "+entry.getKey());
        }
        for(var entry:chinese.entrySet()) {
            String text=entry.getValue().getAsString();
            require(text.codePoints().anyMatch(c -> c>=0x3400 && c<=0x9fff),"Chinese text missing: "+entry.getKey());
        }
        boolean active="zh_cn".equals(game.options.languageCode);
        if(active) {
            require(I18n.get("itemGroup.mfqm").equals(chinese.get("itemGroup.mfqm").getAsString()),"loaded Chinese creative tab");
            for(var item:BuiltInRegistries.ITEM)if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(MFQM.MOD_ID))
                require(chinese.has(item.getDescriptionId()),"registered item name: "+item.getDescriptionId());
            for(var type:BuiltInRegistries.ENTITY_TYPE)if(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(MFQM.MOD_ID))
                require(chinese.has(type.getDescriptionId()),"registered entity name: "+type.getDescriptionId());
            require(!I18n.get("mfqm.configuration.creativeGroundPhysics").equals("mfqm.configuration.creativeGroundPhysics"),"loaded Chinese settings");
        }
        MFQM.LOGGER.info("MFQM_CHINESE_CHECKS_COMPLETE englishKeys={} chineseKeys={} active={} configScreen=true formats=true registeredNames=true",
                english.size(),chinese.size(),active);
    }
    private static JsonObject read(Minecraft game,String locale) throws java.io.IOException {
        try(var reader=game.getResourceManager().getResource(Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"lang/"+locale+".json"))
                .orElseThrow().openAsReader()) {return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    private static java.util.List<String> formats(String value) {
        var found=new java.util.ArrayList<String>();
        var matcher=java.util.regex.Pattern.compile("%(?:[0-9]+\\$)?[a-zA-Z]").matcher(value);
        while(matcher.find())found.add(matcher.group());
        java.util.Collections.sort(found);return found;
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
    private ChineseLanguageChecks(){}
}
