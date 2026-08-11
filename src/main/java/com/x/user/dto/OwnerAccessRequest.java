package com.x.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OwnerAccessRequest(@NotNull List<@Positive Long> storeIds) {
}
