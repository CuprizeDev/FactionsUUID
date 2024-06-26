package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.event.FPlayerToggleStealthEvent;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import org.bukkit.Bukkit;

public class CmdStealth extends FCommand{
    public CmdStealth(){
        this.aliases.add("stealth");
        this.aliases.add("ninja");

        this.requirements = new CommandRequirements.Builder(Permission.STEALTH).playerOnly().memberOnly().build();
    }

    @Override
    public void perform(CommandContext context) {
        FPlayer fPlayer = context.fPlayer;
        FPlayerToggleStealthEvent event = new FPlayerToggleStealthEvent(fPlayer, FPlayerToggleStealthEvent.Type.COMMAND);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled())
            return;

        boolean stealth = fPlayer.isStealth();

        if(stealth){
            context.fPlayer.msg(TL.COMMAND_STEALTH_TOGGLE, TextUtil.parseColor("&cOFF"));
        }else{
            context.fPlayer.msg(TL.COMMAND_STEALTH_TOGGLE, TextUtil.parseColor("&aON"));
        }

        context.fPlayer.setStealthFromCommand(!stealth);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_STEALTH_DESCRIPTION;
    }
}
