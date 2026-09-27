package fr.luc.pinata.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface SubCommand {
    String name();
    String permission();
    String usage();
    String description();

    void execute(CommandSender sender, String[] args);

    default List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
