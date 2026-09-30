package es.mrdino.decoswap.api.event;

import es.mrdino.decoswap.decoration.Decoration;
import es.mrdino.decoswap.deployment.Deployment;
import org.bukkit.event.*;
import org.jetbrains.annotations.NotNull;

public final class DecorationDeployEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Decoration decoration;
  private final Deployment deployment;

  public DecorationDeployEvent(Decoration d, Deployment p) {
    decoration = d;
    deployment = p;
  }

  public Decoration decoration() {
    return decoration;
  }

  public Deployment deployment() {
    return deployment;
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
