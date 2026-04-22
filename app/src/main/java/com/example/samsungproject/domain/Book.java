package com.example.samsungproject.domain;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Book {
    private final long id;
    @Nullable private String password;
    @Nullable private String name;
    @Nullable private byte[] icon;
    @Nullable private String description;
    private final List<String> tags;

    public Book(
            long id,
            @Nullable String password,
            @Nullable String name,
            @Nullable byte[] icon,
            @Nullable String description,
            @Nullable List<String> tags
    ) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.icon = (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
        this.description = description;
        this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public byte[] getIcon() {
        return (icon != null) ? Arrays.copyOf(icon, icon.length) : null;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void changeName(@Nullable String newName) {
        this.name = newName;
    }

    public void changeIcon(@Nullable byte[] newIcon) {
        this.icon = (newIcon != null) ? Arrays.copyOf(newIcon, newIcon.length) : null;
    }

    public void changeDescription(@Nullable String newDescription) {
        this.description = newDescription;
    }

    public void changeTags(@Nullable List<String> tagsToAdd, @Nullable List<String> tagsToRemove) {
        if (tagsToRemove != null) {
            for (String tag : tagsToRemove) {
                if (tag != null) {
                    while (this.tags.remove(tag)) {
                    }
                }
            }
        }
        if (tagsToAdd != null) {
            for (String tag : tagsToAdd) {
                if (tag != null && !this.tags.contains(tag)) {
                    this.tags.add(tag);
                }
            }
        }
    }
    public void changeTags(@Nullable List<String> newTags) {
        this.tags.clear();
        if (newTags != null) {
            for (String tag : newTags) {
                if (tag != null && !this.tags.contains(tag)) {
                    this.tags.add(tag);
                }
            }
        }
    }
}
