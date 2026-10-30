package de.quest.ambient;

import de.quest.entity.AmbientAdventurerEntity;
import de.quest.kingdom.NpcSpeechService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Only observed local conditions are mentioned; conversations give no quest or economic benefit. */
public final class AmbientAdventurerDialogue {
    private AmbientAdventurerDialogue() {}
    public static void warning(ServerPlayer player,AmbientAdventurerEntity npc) { say(player,npc,"warning"); }
    public static String context(boolean refused,boolean danger,boolean cave,boolean rain,boolean night,boolean hunter) {
        if(refused) return "refusal";
        if(danger) return "danger";
        if(cave) return "cave";
        if(rain) return "rain";
        if(hunter) return night?"hunt":"dawn";
        return night?"night":"day";
    }
    public static boolean greet(ServerPlayer player,AmbientAdventurerEntity npc,boolean refused,boolean danger) {
        var world=npc.level();
        return say(player,npc,context(refused,danger,!world.canSeeSky(npc.blockPosition()),world.isRainingAt(npc.blockPosition()),
                AmbientAdventurerPolicy.night(world.getOverworldClockTime()),npc.isNightrunner()));
    }
    private static boolean say(ServerPlayer player,AmbientAdventurerEntity npc,String context) {
        return NpcSpeechService.privateLine(player,npc,Component.translatable("message.village-quest.ambient."+(npc.isNightrunner()?"nightrunner.":"wanderer.")+context));
    }
}
