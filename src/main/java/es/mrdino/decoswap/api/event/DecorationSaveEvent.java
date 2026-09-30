package es.mrdino.decoswap.api.event;

import es.mrdino.decoswap.decoration.Decoration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.jetbrains.annotations.NotNull;

public final class DecorationSaveEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Decoration decoration;
  private final Player actor;

  public DecorationSaveEvent(Decoration d, Player p) {
    decoration = d;
    actor = p;
  }

  public Decoration decoration() {
    return decoration;
  }

  public Player actor() {
    return actor;
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
