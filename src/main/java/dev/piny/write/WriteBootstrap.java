package dev.piny.write;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piny.write.command.GenerateCommand;
import dev.piny.write.command.GiveCommand;
import dev.piny.write.command.IntegrationCommand;
import dev.piny.write.command.ManageCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

class WriteBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        // Plugin bootstrap logic
        boolean isDev = !System.getProperty("xyz.jpenilla.run-task", "false").equals("false");

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            LiteralArgumentBuilder<CommandSourceStack> nodes = Commands.literal("write")
                            .then(
                                    new GiveCommand().create()
                            )
                            .then(
                                    new IntegrationCommand().create()
                            )
                            .then(
                                    new ManageCommand().create()
                            );

            if (isDev) {
                nodes.then(
                        new GenerateCommand().create()
                );
            }

            commands.registrar().register(nodes.build(), List.of("wr"));
        });
    }
}
