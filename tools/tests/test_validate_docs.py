import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('validate_docs', Path(__file__).parents[1] / 'validate_docs.py')
docs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(docs)


class DocumentationChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, name, text=''):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        return path

    def test_existing_link_and_external_url(self):
        self.write('a.md')
        page = self.write('README.md', '[a](a.md#heading) [web](https://example.com/a)')
        self.assertEqual([], docs.check_links(self.root, page))

    def test_missing_link_reports_file_and_line(self):
        page = self.write('README.md', '# Title\n[missing](missing.md)')
        self.assertIn('README.md:2', docs.check_links(self.root, page)[0])

    def test_code_examples_do_not_require_files(self):
        page = self.write('README.md', '```markdown\n[example](absent.md)\n```\n`[inline](absent.md)`')
        self.assertEqual([], docs.check_links(self.root, page))

    def test_reference_and_percent_encoded_paths(self):
        self.write('with space.md')
        page = self.write('README.md', '[page][guide]\n[guide]: with%20space.md\n[inline](<with space.md>)')
        self.assertEqual([], docs.check_links(self.root, page))

    def test_outside_repository_is_rejected(self):
        page = self.write('README.md', '[outside](../elsewhere.md)')
        self.assertIn('outside repository', docs.check_links(self.root, page)[0])

    def test_wrong_case_is_rejected_on_windows_too(self):
        self.write('Actual.md')
        page = self.write('README.md', '[case](actual.md)')
        self.assertTrue(docs.check_links(self.root, page))

    def test_wiki_page_must_be_in_navigation(self):
        self.write('26.3/docs/wiki/README.md', '# Wiki')
        self.write('26.3/docs/wiki/new-feature.md')
        self.assertIn('new-feature.md', docs.check_navigation(self.root, '26.3')[0])

    def test_unreleased_is_not_published_stable(self):
        self.write('26.3/CHANGELOG.md', '## 2.5.0-unreleased.8\n## 2.4.2\n## 2.4.1')
        self.write('26.3/README.md', 'Current stable release: `2.4.1`')
        self.write('26.3/docs/wiki/README.md', 'Current stable release: `2.4.2`')
        errors = docs.check_stable(self.root, '26.3')
        self.assertEqual(1, len(errors))
        self.assertIn('2.4.2', errors[0])

    def test_root_release_list_checks_each_target(self):
        self.write('README.md', 'Current stable releases:\n\n- `2.4.1 - Title` for Minecraft `26.3` and `26.2`;\n\nRelease details.')
        self.write('26.3/CHANGELOG.md', '## 2.4.2')
        self.write('26.2/CHANGELOG.md', '## 2.4.1')
        errors = docs.check_root_stable(self.root, ['26.3', '26.2'])
        self.assertEqual(1, len(errors))
        self.assertIn('26.3', errors[0])


if __name__ == '__main__':
    unittest.main()
