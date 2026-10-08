"""Guard the no-image/actual-JAR launcher contract without pretending to run Minecraft."""
import unittest

from smoke_viscosity_client import viscosity_vm, upstream_skin_pack_metadata_error
from smoke_installed_client import ROOT, workspace_path


class ViscosityLauncherTest(unittest.TestCase):
    def test_texture_runtime_cannot_enable_images(self):
        prepared = "-Xmx3G\n-Dmfqm.textureChecks=true\n  -Dmfqm.textureChecks=true\n-Dmfqm.clientChecks=false\n"
        actual = viscosity_vm(prepared, False)
        self.assertNotIn("-Dmfqm.textureChecks=true", actual)
        self.assertEqual(actual.count("-Dmfqm.textureChecks="), 1)
        self.assertIn("-Dmfqm.textureChecks=false\n", actual)
        self.assertIn("-Dmfqm.clientChecks=true\n", actual)
        self.assertIn("-Dmfqm.installedChecks=true\n", actual)
        self.assertIn("-Dmfqm.viscosityFlightChecks=false\n", actual)
        self.assertTrue(actual.startswith("-Xmx3G\n"))

    def test_default_preserves_creative_ground_immunity(self):
        actual = viscosity_vm("", False)
        self.assertIn("-Dmfqm.viscosityCreativeGroundTraps=false\n", actual)
        self.assertIn("-Dmfqm.viscosityFailFast=true\n", actual)
        self.assertIn("-Dmfqm.viscosityFlightChecks=false\n", actual)

    def test_full_diagnostics_and_ground_opt_in_override_stale_flags(self):
        stale = "-Dmfqm.viscosityCreativeGroundTraps=false\n-Dmfqm.viscosityFailFast=true\n-Dmfqm.installedChecks=false\n-Dmfqm.viscosityFlightChecks=true\n"
        actual = viscosity_vm(stale, True, False)
        self.assertEqual(actual.count("-Dmfqm.viscosityCreativeGroundTraps="), 1)
        self.assertIn("-Dmfqm.viscosityCreativeGroundTraps=true\n", actual)
        self.assertEqual(actual.count("-Dmfqm.viscosityFailFast="), 1)
        self.assertIn("-Dmfqm.viscosityFailFast=false\n", actual)
        self.assertNotIn("-Dmfqm.installedChecks=false", actual)
        self.assertNotIn("-Dmfqm.viscosityFlightChecks=true", actual)
        self.assertIn("-Dmfqm.viscosityFlightChecks=false\n", actual)

    def test_flight_controls_require_explicit_separate_opt_in(self):
        actual = viscosity_vm("-Dmfqm.viscosityFlightChecks=false\n", False, True, True)
        self.assertEqual(actual.count("-Dmfqm.viscosityFlightChecks="), 1)
        self.assertIn("-Dmfqm.viscosityFlightChecks=true\n", actual)

    def test_dedicated_instance_stays_inside_workspace(self):
        expected = ROOT / "run/1.21.11/viscosity-client"
        self.assertEqual(workspace_path(expected), expected.resolve())
        with self.assertRaisesRegex(ValueError, "escapes the workspace"):
            workspace_path(ROOT.parent / "desktop-export")

    def test_board_image_requires_explicit_opt_in(self):
        stale="-Dmfqm.boardVisual=true\n-Dmfqm.boundedOnly=true\n"
        default=viscosity_vm(stale,False)
        self.assertIn("-Dmfqm.boardVisual=false\n",default)
        self.assertIn("-Dmfqm.boundedOnly=false\n",default)
        actual=viscosity_vm(stale,True,bounded_only=True,board_visual=True)
        self.assertEqual(actual.count("-Dmfqm.boardVisual="),1)
        self.assertIn("-Dmfqm.boardVisual=true\n",actual)
        self.assertIn("-Dmfqm.textureChecks=false\n",actual)

    def test_action_scope_is_explicit_and_clears_stale_flag(self):
        self.assertIn("-Dmfqm.actionsOnly=false\n",viscosity_vm("-Dmfqm.actionsOnly=true\n",True))
        actual=viscosity_vm("-Dmfqm.actionsOnly=false\n",True,actions_only=True)
        self.assertEqual(actual.count("-Dmfqm.actionsOnly="),1)
        self.assertIn("-Dmfqm.actionsOnly=true\n",actual)
        self.assertIn("-Dmfqm.boardVisual=false\n",actual)

    def test_coating_and_optional_mod_are_explicit_and_clear_stale_flags(self):
        stale="-Dmfqm.coatingChecks=true\n-Dmfqm.coatingVisual=true\n-Dmfqm.coatingSkinLayersExpected=true\n"
        default=viscosity_vm(stale,True)
        for key in ("coatingChecks","coatingVisual","coatingSkinLayersExpected"):
            self.assertEqual(default.count("-Dmfqm."+key+"="),1)
            self.assertIn("-Dmfqm."+key+"=false\n",default)
        actual=viscosity_vm(stale,True,coating_only=True,skin_layers=True,coating_visual=True)
        self.assertIn("-Dmfqm.coatingChecks=true\n",actual)
        self.assertIn("-Dmfqm.coatingSkinLayersExpected=true\n",actual)
        self.assertIn("-Dmfqm.coatingVisual=true\n",actual)
        self.assertIn("-Dmfqm.textureChecks=false\n",actual)

    def test_only_exact_upstream_metadata_errors_are_recorded_in_compat_run(self):
        line="[Render thread/ERROR] [minecraft/AbstractPackResources]: Couldn't load mod/skinlayers3d pack metadata: Pack declares support for format 75, but game versions supporting formats 17 to 81 require a supported_formats field."
        self.assertTrue(upstream_skin_pack_metadata_error(line, True))
        self.assertFalse(upstream_skin_pack_metadata_error(line, False))
        self.assertFalse(upstream_skin_pack_metadata_error(line.replace("skinlayers3d", "mfqm"), True))
        self.assertFalse(upstream_skin_pack_metadata_error("[Render thread/ERROR] GPU rendering failed", True))

    def test_first_person_is_explicit_and_clears_stale_flag(self):
        stale="-Dmfqm.firstPersonExpected=true\n"
        self.assertIn("-Dmfqm.firstPersonExpected=false\n",viscosity_vm(stale,True))
        actual=viscosity_vm(stale,True,coating_only=True,first_person=True)
        self.assertEqual(actual.count("-Dmfqm.firstPersonExpected="),1)
        self.assertIn("-Dmfqm.firstPersonExpected=true\n",actual)

    def test_dense_scene_and_image_are_explicit(self):
        stale="-Dmfqm.dynamicAdhesionChecks=true\n-Dmfqm.dynamicVisual=true\n"
        actual=viscosity_vm(stale,True)
        self.assertIn("-Dmfqm.dynamicAdhesionChecks=false\n",actual)
        self.assertIn("-Dmfqm.dynamicVisual=false\n",actual)
        dense=viscosity_vm(stale,True,dynamic_only=True,dynamic_visual=True)
        self.assertEqual(dense.count("-Dmfqm.dynamicVisual="),1)
        self.assertIn("-Dmfqm.dynamicVisual=true\n",dense)

    def test_animation_compatibility_is_explicit_and_exact(self):
        stale="-Dmfqm.coatingAnimationsExpected=true\n"
        self.assertIn("-Dmfqm.coatingAnimationsExpected=false\n",viscosity_vm(stale,True))
        actual=viscosity_vm(stale,True,coating_only=True,first_person=True,animations=True)
        self.assertEqual(actual.count("-Dmfqm.coatingAnimationsExpected="),1)
        self.assertIn("-Dmfqm.coatingAnimationsExpected=true\n",actual)
        line="[Render thread/ERROR] [minecraft/AbstractPackResources]: Couldn't load mod/notenoughanimations pack metadata: Pack declares support for format 75, but game versions supporting formats 17 to 81 require a supported_formats field."
        self.assertFalse(upstream_skin_pack_metadata_error(line,True,True))
        self.assertTrue(upstream_skin_pack_metadata_error(line,True,True,True))
        self.assertFalse(upstream_skin_pack_metadata_error(line.replace("notenoughanimations","mfqm"),True,True,True))


if __name__ == "__main__":
    unittest.main()
