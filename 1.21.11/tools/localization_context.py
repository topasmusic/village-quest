"""Standalone context guards, copied from repository tools/validate_resources.py.

Parity is checked by tools/tests/test_validate_resources.py.
"""
import re

DAMAGED_UNICODE = re.compile(
    r"(?:Ã.|Â.|â[€†š]|ðŸ|ï¿½|[\u0080-\u009f\u200b-\u200f\ufeff\ufffd])"
)

from collections import Counter
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z]")

def placeholders(value: object) -> Counter[str]:
    if not isinstance(value, str):
        return Counter()
    return Counter(PLACEHOLDER.findall(value))

def validate_catalog_context(language: str, catalog: dict[str, object]) -> list[str]:
    """Guard confirmed semantic regressions, not arbitrary editorial wording.

    Evidence: PilgrimContractService spider objective, StonesThatRemember's two
    surveys, the ledger's route permit paths, and JournalScreen's label strip.
    Changes to those mechanics require reviewing these expectations as well.
    """
    errors: list[str] = []
    labels = {'en_us':'Effect:', 'de_de':'Wirkung:', 'es_es':'Efecto:'}
    two = {'en_us':r'\b(?:two|2)\b', 'de_de':r'\b(?:zwei|2)\b', 'es_es':r'\b(?:dos|2)\b'}
    route = {'en_us':r'\broute\b', 'de_de':r'\bDorfroute\b', 'es_es':r'\bruta\b'}
    compass = {'en_us':r'\b(?:Compass|compass)\b', 'de_de':r'\bKompass\b', 'es_es':r'\b[Bb]rújula\b'}
    title = catalog.get('quest.village-quest.story.market_road_troubles.title')
    for key, value in catalog.items():
        if not isinstance(value,str): continue
        reason = None
        # Commands are stable literals; proper item branding is not translated.
        if language=='de_de' and re.search(r'\b(?:Wallet|Ledger|Questmaster|Wayshrine|Homestead|Farming-Ruf|Crafting-Ruf)\b',value):
            reason='untranslated German player-facing term'
        elif language=='es_es' and re.search(r'\b(?:Wayfinder|Orientador)\b',value):
            reason='obsolete compass alias'
        if key=='quest.village-quest.pilgrim.contract.fangs_by_the_hedgerow.title' and language=='de_de' and 'Fangzähne' not in value:
            reason='spider contract title must refer to fangs'
        if key=='item.village-quest.cartographers_lens.lore.3' and not re.search(two[language],value,re.I) and placeholders(value) != Counter({'%s':1}):
            reason='lens is installed after two village surveys'
        if key=='screen.village-quest.guild_path.node.ledger.requirement' and isinstance(title,str) and title not in value:
            # TradeRouteService.hasRouteAccess/initializeProvisionalNetwork also
            # grants the ledger via the local permit from Living Kingdoms ch. 2.
            charter=catalog.get('quest.village-quest.project.market_charter.title')
            kingdom=catalog.get('quest.village-quest.story.living_kingdoms.title')
            if not (isinstance(charter,str) and charter and charter in value
                    and isinstance(kingdom,str) and kingdom and kingdom in value):
                reason='ledger requirement must name the market story or both localized route permit paths'
        if key.startswith('quest.village-quest.project.') and key.endswith('.effect') and not value.startswith(labels[language]):
            reason='effect label does not match the Atlas heading'
        if key in ('quest.village-quest.project.market_charter.effect','screen.village-quest.questmaster.reward.effect.market_charter') and (not re.search(route[language],value) or not re.search(compass[language],value)):
            reason='Market Charter must explain its initial route and compass upgrade'
        if reason: errors.append(f'{language}: {reason} for {key}')
    return errors
