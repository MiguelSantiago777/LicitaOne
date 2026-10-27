package br.com.shema.crm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Quebra o build se um módulo acessar a parte interna de outro ou se surgir dependência circular. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ShemacrmApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
