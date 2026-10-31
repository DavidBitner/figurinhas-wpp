package com.davidbitner.stickers;

import java.util.ArrayList;
import java.util.List;

final class Pack {
    static final String TRAY_FILE = "tray.png";
    static final int MIN_STICKERS = 3;
    static final int MAX_STICKERS = 30;

    final String id;
    final String name;
    final boolean animated;
    long version;
    final List<Item> items = new ArrayList<>();

    static final class Item {
        final String file;
        final String emoji;

        Item(String file, String emoji) {
            this.file = file;
            this.emoji = emoji;
        }
    }

    Pack(String id, String name, boolean animated) {
        this.id = id;
        this.name = name;
        this.animated = animated;
    }

    boolean isReady() {
        return items.size() >= MIN_STICKERS;
    }

    Pack copy() {
        Pack p = new Pack(id, name, animated);
        p.version = version;
        p.items.addAll(items);
        return p;
    }
}
