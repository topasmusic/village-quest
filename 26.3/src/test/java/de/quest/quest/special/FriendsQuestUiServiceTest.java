package de.quest.quest.special;

import de.quest.network.FriendsQuestPayloads.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FriendsQuestUiServiceTest {
    private View view(UUID id,long revision,int chapter,boolean active,boolean enabled) {
        var c=Component.literal("test");return new View(id,revision,false,true,"simon",chapter,active,c,c,c,List.of(),c,List.of(),List.of(),List.of(new Option(active?"claim":"accept",c,enabled)));
    }
    @Test void onlyTheDisplayedEnabledActionAtTheSameStageCanExecute() {
        var id=UUID.randomUUID();var shown=view(id,7,1,true,true);var action=new Action(id,7,"claim");
        assertTrue(FriendsQuestUiService.matches(shown,action,shown));
        assertFalse(FriendsQuestUiService.matches(shown,new Action(id,6,"claim"),shown));
        assertFalse(FriendsQuestUiService.matches(shown,new Action(UUID.randomUUID(),7,"claim"),shown));
        assertFalse(FriendsQuestUiService.matches(shown,new Action(id,7,"accept"),shown));
        assertFalse(FriendsQuestUiService.matches(shown,action,view(id,7,2,false,true)));
        assertFalse(FriendsQuestUiService.matches(shown,action,view(id,7,1,true,false)));
    }
}
