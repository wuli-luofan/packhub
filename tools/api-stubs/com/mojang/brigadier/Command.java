package com.mojang.brigadier;
@FunctionalInterface
public interface Command<S> {
    int run(CommandContext<S> context);
}