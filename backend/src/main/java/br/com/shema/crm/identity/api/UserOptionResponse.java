package br.com.shema.crm.identity.api;

import br.com.shema.crm.identity.UserSummary;
import java.util.UUID;

public record UserOptionResponse(UUID id, String name, String initials) {

    static UserOptionResponse from(UserSummary user) {
        return new UserOptionResponse(user.id(), user.name(), user.initials());
    }
}
