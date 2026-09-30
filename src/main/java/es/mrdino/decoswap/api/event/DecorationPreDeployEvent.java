package es.mrdino.decoswap.api.event;

import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.decoration.Decoration;
import org.bukkit.event.*;
import org.jetbrains.annotations.NotNull;

public final class DecorationPreDeployEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Decoration decoration;
  private final Anchor target;
  private boolean cancelled;

  public DecorationPreDeployEvent(Decoration d, Anchor t) {
    decoration = d;
    target = t;
  }

  public Decoration decoration() {
    return decoration;
  }

  public Anchor target() {
    return target;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean c) {
    cancelled = c;
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
