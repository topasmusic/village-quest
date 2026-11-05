package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class RuleTextPresentationTest {
    private static boolean key(Component c,String key) {
        if(c.getContents() instanceof TranslatableContents t) {
            if(t.getKey().equals(key))return true;
            for(var arg:t.getArgs())if(arg instanceof Component child && key(child,key))return true;
        }
        return c.getSiblings().stream().anyMatch(child->key(child,key));
    }
    @Test void explicitRulesKeepOriginalArgumentsAndAreNotDuplicatedOnRepeatedPresentation() {
        var amount=7;
        String key="quest.village-quest.daily.wheat.offer.2";
        var original=Component.translatable(key,amount);
        var extended=QuestRuleText.extend(original);
        assertEquals(amount,((TranslatableContents)extended.getContents()).getArgs()[0]);
        assertTrue(key(extended,key+".rules"));assertTrue(original.getSiblings().isEmpty());
        assertEquals(extended,QuestRuleText.extend(extended));
        var unaffected=Component.translatable("quest.village-quest.daily.potato.offer.2");
        assertEquals(unaffected,QuestRuleText.extend(unaffected));
    }
    @Test void dynamicStoryFactoriesAndFriendObjectivesActuallyIncludeTheReviewedRules() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var shrine=new de.quest.content.story.ShrinesBetweenRoadsStoryArc();
        for(int chapter:List.of(0,3,4))assertTrue(key(shrine.chapter(chapter).offerParagraph1(),
                "quest.village-quest.story.shrines_between_roads.chapter_"+(chapter+1)+".offer.1.rules"));
        var caravan=new de.quest.content.story.TheEmptyCaravanStoryArc();
        assertTrue(key(caravan.chapter(4).offerParagraph1(),"quest.village-quest.story.the_empty_caravan.chapter_5.offer.1.rules"));
        assertTrue(key(caravan.chapter(4).offerParagraph2(),"quest.village-quest.story.the_empty_caravan.chapter_5.offer.2.rules"));
        var artifacts=new de.quest.content.story.ArtifactJourneyStoryArc();
        for(int chapter=0;chapter<3;chapter++) {
            String expected="quest.village-quest.story.artifact_journey.chapter_"+(chapter+1)+".instructions.rules";
            assertTrue(artifacts.chapter(chapter).descriptionLines().stream().anyMatch(c->key(c,expected)));
        }
        assertTrue(key(de.quest.quest.special.FriendsQuestService.tr("lutte.2.objective"),"quest.village-quest.friends.lutte.2.objective.rules"));
        assertTrue(key(de.quest.quest.special.FriendsQuestService.tr("marius.1.objective"),"quest.village-quest.friends.marius.1.objective.rules"));
    }
}
