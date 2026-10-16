package net.fabricmc.fabric.api.event;
public interface Event<T> {
    void register(T listener);
}