package es.mrdino.decoswap.api.event;

import es.mrdino.decoswap.group.DecorationGroup;
import org.bukkit.event.*;
import org.jetbrains.annotations.NotNull;

public final class GroupDeployEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final DecorationGroup group;

  public GroupDeployEvent(DecorationGroup g) {
    group = g;
  }

  public DecorationGroup group() {
    return group;
  }

  @NotNull
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  @NotNull
  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
