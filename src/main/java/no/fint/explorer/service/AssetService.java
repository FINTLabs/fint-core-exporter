package no.fint.explorer.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.explorer.factory.AssetFactory;
import no.fint.explorer.model.Asset;
import no.fint.explorer.model.SseOrg;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AssetService {
    private final ProviderService providerService;
    private final ConsumerService consumerService;

    private final Map<String, Asset> assets = new ConcurrentSkipListMap<>();

    public AssetService(ProviderService providerService, ConsumerService consumerService) {
        this.providerService = providerService;
        this.consumerService = consumerService;
    }

    public Collection<Asset> getAssets() {
        return assets.values();
    }

    public Asset getAsset(String id) {
        return assets.get(id);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 600000)
    public void update() {
        log.info("Updating assets");

        providerService.getProviders()
                .stream()
                .map(providerService::getSseOrgs)
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(SseOrg::getOrgId))
                .entrySet()
                .stream()
                .map(AssetFactory::toAsset)
                .peek(this::addHealth)
                .forEach(asset -> assets.put(asset.getId(), asset));

        log.info("Finished updating assets");
    }

    private void addHealth(Asset asset) {
        asset.getComponents()
                .stream()
                .filter(component -> !component.getClients().isEmpty())
                .forEach(component -> consumerService.getConsumer(component.getId()).ifPresent(service ->
                        component.setHealth(consumerService.getHealth(service, asset.getId()))));
    }
}
