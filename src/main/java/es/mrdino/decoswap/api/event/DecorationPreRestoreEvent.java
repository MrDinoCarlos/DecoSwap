package es.mrdino.decoswap.api.event;

import es.mrdino.decoswap.deployment.Deployment;
import org.bukkit.event.*;
import org.jetbrains.annotations.NotNull;

public final class DecorationPreRestoreEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Deployment deployment;
  private boolean cancelled;

  public DecorationPreRestoreEvent(Deployment d) {
    deployment = d;
  }

  public Deployment deployment() {
    return deployment;
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
