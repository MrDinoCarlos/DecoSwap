package es.mrdino.decoswap.api.event;

import es.mrdino.decoswap.deployment.Deployment;
import org.bukkit.event.*;
import org.jetbrains.annotations.NotNull;

public final class DecorationRestoreEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Deployment deployment;
  private final int missingEntities, skippedConflicts;

  public DecorationRestoreEvent(Deployment d, int m, int s) {
    deployment = d;
    missingEntities = m;
    skippedConflicts = s;
  }

  public Deployment deployment() {
    return deployment;
  }

  public int missingEntities() {
    return missingEntities;
  }

  public int skippedConflicts() {
    return skippedConflicts;
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
