package br.com.sistema.triagem.service;

import br.com.sistema.triagem.client.EvolutionApiClient;
import br.com.sistema.triagem.config.EvolutionApiConfig.Properties;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EvolutionProvisioningService {
    private final EvolutionApiClient client;
    private final Properties config;
    private final boolean provisionEnabled;
    public EvolutionProvisioningService(EvolutionApiClient client, Properties config,
            @org.springframework.beans.factory.annotation.Value("${evolution.provision-enabled:true}") boolean provisionEnabled) {
        this.client=client; this.config=config; this.provisionEnabled=provisionEnabled;
    }

    @Scheduled(initialDelayString = "5000", fixedDelayString = "${evolution.provision-delay-ms:30000}")
    public void provisionar() {
        if (!provisionEnabled || !config.managed() || !config.enabled()) return;
        try { client.prepararInstancia(); }
        catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Evolution indisponível para configurar a instância; nova tentativa agendada");
        }
    }
}
