package br.com.shema.crm.tenders.api;

import br.com.shema.crm.shared.web.PageResponse;
import br.com.shema.crm.tenders.application.AgencyService;
import br.com.shema.crm.tenders.domain.Agency;
import br.com.shema.crm.tenders.domain.AgencyContact;
import br.com.shema.crm.tenders.domain.Sphere;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agencies")
@Tag(name = "agencies", description = "Órgãos")
class AgencyController {

    record AgencyRequest(
            @NotBlank(message = "Informe o nome do órgão") @Size(max = 200)
            String name,

            @NotNull(message = "Escolha a esfera") Sphere sphere,

            @NotBlank(message = "Informe o município") @Size(max = 120)
            String city,

            @NotBlank(message = "Informe a UF") @Size(min = 2, max = 2, message = "UF tem 2 letras")
            String state,

            @Size(max = 18) String cnpj,
            @Size(max = 10) String defaultUasg,
            @Size(max = 2000) String notes,
            @PositiveOrZero Long version) {

        AgencyService.AgencyInput toInput() {
            return new AgencyService.AgencyInput(name, sphere, city, state, cnpj, defaultUasg, notes);
        }
    }

    record AgencyResponse(
            UUID id,
            String name,
            Sphere sphere,
            String sphereLabel,
            String city,
            String state,
            String cnpj,
            String defaultUasg,
            String notes,
            long version) {

        static AgencyResponse from(Agency a) {
            return new AgencyResponse(
                    a.getId(),
                    a.getName(),
                    a.getSphere(),
                    a.getSphere().label(),
                    a.getCity(),
                    a.getState(),
                    a.getCnpj(),
                    a.getDefaultUasg() == null ? null : a.getDefaultUasg().value(),
                    a.getNotes(),
                    a.getVersion() == null ? 0 : a.getVersion());
        }
    }

    record ContactRequest(
            @NotBlank(message = "Informe o nome do contato") @Size(max = 120)
            String name,

            @Size(max = 80) String role,
            @Size(max = 160) String email,
            @Size(max = 25) String phone,
            @Size(max = 500) String notes) {}

    record ReplaceContactsRequest(
            @NotNull(message = "Versão obrigatória") @PositiveOrZero
            Long version,

            @NotNull @Valid @Size(max = 20) List<ContactRequest> contacts) {}

    record ContactResponse(UUID id, String name, String role, String email, String phone, String notes) {
        static ContactResponse from(AgencyContact c) {
            return new ContactResponse(c.getId(), c.getName(), c.getRole(), c.getEmail(), c.getPhone(), c.getNotes());
        }
    }

    /** @param agencyVersion versão do órgão depois da alteração, para a próxima edição */
    record ContactsResponse(long agencyVersion, List<ContactResponse> contacts) {}

    private final AgencyService service;

    AgencyController(AgencyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Busca órgãos por nome ou município", operationId = "searchAgencies")
    PageResponse<AgencyResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name"));
        return PageResponse.of(service.search(q, pageable), AgencyResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Um órgão", operationId = "getAgency")
    AgencyResponse get(@PathVariable UUID id) {
        return AgencyResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Cadastra um órgão", operationId = "createAgency")
    AgencyResponse create(@Valid @RequestBody AgencyRequest request) {
        return AgencyResponse.from(service.create(request.toInput()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Altera um órgão", operationId = "updateAgency")
    AgencyResponse update(@PathVariable UUID id, @Valid @RequestBody AgencyRequest request) {
        long version = request.version() == null ? -1 : request.version();
        return AgencyResponse.from(service.update(id, version, request.toInput()));
    }

    @GetMapping("/{id}/contacts")
    @Operation(summary = "Contatos do órgão", operationId = "agencyContacts")
    ContactsResponse contacts(@PathVariable UUID id) {
        Agency agency = service.get(id);
        return new ContactsResponse(
                agency.getVersion() == null ? 0 : agency.getVersion(),
                service.contacts(id).stream().map(ContactResponse::from).toList());
    }

    @PutMapping("/{id}/contacts")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    @Operation(summary = "Substitui os contatos do órgão", operationId = "replaceAgencyContacts")
    ContactsResponse replaceContacts(@PathVariable UUID id, @Valid @RequestBody ReplaceContactsRequest request) {
        Agency agency = service.replaceContacts(
                id,
                request.version(),
                request.contacts().stream()
                        .map(c -> new AgencyService.ContactInput(c.name(), c.role(), c.email(), c.phone(), c.notes()))
                        .toList());
        return new ContactsResponse(
                agency.getVersion() == null ? 0 : agency.getVersion(),
                service.contacts(id).stream().map(ContactResponse::from).toList());
    }
}
