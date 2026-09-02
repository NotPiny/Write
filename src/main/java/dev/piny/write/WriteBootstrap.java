package dev.piny.write;

import dev.piny.write.command.GiveCommand;
import dev.piny.write.command.IntegrationCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

class WriteBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        // Plugin bootstrap logic
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(Commands.literal("write")
                    .then(
                            new GiveCommand().create()
                    )
                    .then(
                            new IntegrationCommand().create()
                    )
                    .build(),
                    List.of("wr")
            );
        });
    }
}
