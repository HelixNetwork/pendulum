package net.helix.hlx;

import java.util.Arrays;
import java.util.Optional;

public enum XiEvent {
    CREATE_MODULE("ENTRY_CREATE"),
    MODIFY_MODULE("ENTRY_MODIFY"),
    DELETE_MODULE("ENTRY_DELETE"),
    OVERFLOW("OVERFLOW"),
    UNKNOWN("UNKNOWN");

    private String name;

    XiEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static XiEvent fromName(String name) {
        Optional<XiEvent> ixiEvent = Arrays.stream(XiEvent.values()).filter(event -> event.name.equals(name)).findFirst();
        return ixiEvent.orElse(UNKNOWN);
    }
}
