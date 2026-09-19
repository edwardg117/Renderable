package io.github.edwardg117.renderable.ui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.RenderableConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client-side /renderable command tree (spec §5.1). */
public final class RenderableCommand {

    private RenderableCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        ProfilerController c = ProfilerController.get();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("renderable")
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            c.start(RenderableConfig.DEFAULT_DURATION_SECONDS.get());
                            return 1;
                        })
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(2, 600))
                                .executes(ctx -> {
                                    c.start(IntegerArgumentType.getInteger(ctx, "seconds"));
                                    return 1;
                                })))
                .then(Commands.literal("stop").executes(ctx -> {
                    c.stop();
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    c.cancel();
                    return 1;
                }))
                .then(Commands.literal("results").executes(ctx -> {
                    c.openResults();
                    return 1;
                }))
                .then(Commands.literal("copy").executes(ctx -> {
                    c.copyReport();
                    return 1;
                }))
                .then(Commands.literal("folder").executes(ctx -> {
                    c.openFolder();
                    return 1;
                }))
                .then(Commands.literal("overlay")
                        .executes(ctx -> {
                            WorldOverlay.toggle();
                            ProfilerController.chat("Renderable: overlay " + (WorldOverlay.isEnabled() ? "on" : "off"));
                            return 1;
                        })
                        .then(Commands.literal("on").executes(ctx -> {
                            WorldOverlay.setEnabled(true);
                            ProfilerController.chat("Renderable: overlay on");
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            WorldOverlay.setEnabled(false);
                            ProfilerController.chat("Renderable: overlay off");
                            return 1;
                        })))
                .then(Commands.literal("debug")
                        .requires(src -> RenderableConfig.DEBUG_COMMANDS.get())
                        .then(Commands.literal("stall")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(1, 5000))
                                        .executes(ctx -> {
                                            c.debugStall(IntegerArgumentType.getInteger(ctx, "ms"));
                                            return 1;
                                        }))));

        event.getDispatcher().register(root);
    }
}
