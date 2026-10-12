"""The quality gate must reject unreviewed changes, not silently refresh them."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / 'localization_quality.py'


class LocalizationQualityTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('localization_quality', MODULE)
        self.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.gate)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / 'src/main/resources/assets/village-quest/lang'
        self.folder.mkdir(parents=True)
        self.key = 'screen.village-quest.questmaster.reward.currency'
        self.catalogs = {'en_us': {self.key: 'Wallet: %s'},
                         'de_de': {self.key: 'Geldbeutel: %s'},
                         'es_es': {self.key: 'Monedero: %s'}}
        self.source = self.root / 'src/main/java/Reward.java'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('class Reward { int silvermarks = 2; }', encoding='utf-8')
        self.write_catalogs()
        self.contract = self.gate.build_review(self.root, 'Initial context review')

    def write_catalogs(self):
        for locale, catalog in self.catalogs.items():
            (self.folder / (locale + '.json')).write_text(json.dumps(catalog), encoding='utf-8')

    def errors(self):
        return self.gate.validate_review(self.root, self.contract)

    def test_reviewed_catalogs_are_accepted(self):
        self.assertEqual([], self.errors())

    def test_new_key_needs_context_and_review_in_all_languages(self):
        for catalog in self.catalogs.values():
            catalog['screen.village-quest.questmaster.new_status'] = 'Ready'
        self.write_catalogs()
        self.assertTrue(any('missing context review' in error for error in self.errors()))

    def test_changed_translation_cannot_reuse_an_old_review(self):
        self.catalogs['de_de'][self.key] = 'Geldbeutel: %s Kronen'
        self.write_catalogs()
        self.assertTrue(any('de_de' in error and 'review is stale' in error for error in self.errors()))

    def test_missing_locale_is_a_failure_instead_of_fallback_success(self):
        del self.catalogs['es_es'][self.key]
        self.write_catalogs()
        self.assertTrue(any('es_es' in error and 'missing translation' in error for error in self.errors()))

    def test_mechanic_changes_force_a_context_review_even_with_unchanged_text(self):
        self.source.write_text('class Reward { int silvermarks = 20; }', encoding='utf-8')
        self.assertTrue(any('source review is stale' in error for error in self.errors()))

    def test_new_source_cannot_bypass_the_mechanic_review(self):
        (self.source.parent / 'NewGate.java').write_text('class NewGate {}', encoding='utf-8')
        self.assertTrue(any('missing source review' in error for error in self.errors()))

    def test_removed_keys_and_sources_require_cleanup_of_the_review_contract(self):
        for catalog in self.catalogs.values():
            catalog.clear()
        self.write_catalogs()
        self.source.unlink()
        errors = self.errors()
        self.assertTrue(any('removed key' in error for error in errors))
        self.assertTrue(any('removed source' in error for error in errors))

    def test_unknown_context_and_empty_review_reason_are_rejected(self):
        entry = self.contract['keys'][self.key]
        entry['context'] = 'unknown-context'
        entry['note'] = ''
        errors = self.errors()
        self.assertTrue(any('unknown context' in error for error in errors))
        self.assertTrue(any('review note' in error for error in errors))

    def test_glossary_is_checked_even_if_someone_refreshes_the_text_hash(self):
        self.catalogs['de_de'][self.key] = 'Wallet: %s'
        self.write_catalogs()
        self.contract = self.gate.build_review(self.root, 'A review cannot waive the glossary')
        self.assertTrue(any('glossary' in error for error in self.errors()))

    def test_command_literals_are_not_mistaken_for_untranslated_terms(self):
        for catalog in self.catalogs.values():
            catalog['command.village-quest.help'] = '/vq admin wallet; /questmaster'
        self.write_catalogs()
        self.contract = self.gate.build_review(self.root, 'Commands keep their literal spelling')
        self.assertEqual([], self.errors())

    def test_source_line_endings_do_not_make_a_review_stale(self):
        self.source.write_bytes(self.source.read_bytes().replace(b';', b';\r\n'))
        # Other content differences still count; only CRLF/LF conversion is harmless.
        self.contract = self.gate.build_review(self.root, 'Multiline source review')
        self.source.write_bytes(self.source.read_bytes().replace(b'\r\n', b'\n'))
        self.assertEqual([], self.errors())

    def test_recipe_or_loot_quantity_changes_also_require_context_review(self):
        recipe=self.root/'src/main/resources/data/village-quest/recipe/reward.json'
        recipe.parent.mkdir(parents=True)
        recipe.write_text('{"result":{"count":2}}',encoding='utf-8')
        self.contract=self.gate.build_review(self.root,'Recipe amount checked against item description')
        recipe.write_text('{"result":{"count":20}}',encoding='utf-8')
        self.assertTrue(any('source review is stale' in error for error in self.errors()))

    def test_path_escape_in_a_source_review_is_rejected(self):
        self.contract['sources']['../../private.java'] = {'sha256': '0'*64, 'note': 'Invalid'}
        self.assertTrue(any('invalid source path' in error for error in self.errors()))

    def test_standalone_checks_are_identical_to_central_gate(self):
        repo=MODULE.parent.parent
        for line in ('26.3','26.2'):
            self.assertEqual(MODULE.read_bytes(),(repo/line/'tools/localization_quality.py').read_bytes())
        self.assertEqual((repo/'26.3/tools/localization-quality.gradle').read_bytes(),
                         (repo/'26.2/tools/localization-quality.gradle').read_bytes())


if __name__ == '__main__':
    unittest.main()
