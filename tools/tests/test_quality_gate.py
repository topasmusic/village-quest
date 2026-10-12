"""Regression coverage for the mandatory no-UI orchestration path."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch


class QualityGateTest(unittest.TestCase):
    def setUp(self):
        path=Path(__file__).resolve().parents[1]/'quality_gate.py'
        spec=importlib.util.spec_from_file_location('quality_gate',path)
        self.gate=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.gate)

    def test_failure_stops_before_later_steps_instead_of_reporting_green(self):
        with patch.object(self.gate,'run_python',side_effect=[0,7]) as run:
            self.assertEqual(7,self.gate.check(Path('.'),['26.3']))
            self.assertEqual(2,run.call_count)

    def test_all_supported_lines_get_resource_and_context_validation(self):
        with patch.object(self.gate,'run_python',return_value=0) as run:
            self.assertEqual(0,self.gate.check(Path('.'),list(self.gate.LINES)))
            commands=[str(arg) for call in run.call_args_list for arg in call.args[1]]
            self.assertEqual(4,commands.count('tools/validate_resources.py'))
            self.assertEqual(4,commands.count('tools/localization_quality.py'))
            self.assertFalse(any('--review-' in value for value in commands),'Builds cannot approve their inputs')

    def test_validator_regressions_run_before_catalog_checks(self):
        with patch.object(self.gate,'run_python',return_value=0) as run:
            self.gate.check(Path('.'),['26.2'])
            first=list(run.call_args_list[0].args[1])
            self.assertIn('unittest',first)
            self.assertIn('discover',first)


if __name__=='__main__':
    unittest.main()
