package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.WheelType;
import pt.rucodel.productionplanning.dto.WheelQuantityDto;
import pt.rucodel.productionplanning.exception.InvalidRequestException;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WheelQuantityService {
    public Map<WheelType, Integer> normalize(List<WheelQuantityDto> quantities, Integer legacyQuantity) {
        if (quantities == null || quantities.isEmpty()) {
            if (legacyQuantity == null) {
                throw new InvalidRequestException("É obrigatório indicar as quantidades por tipo de jante.");
            }
            if (legacyQuantity <= 0) {
                throw new InvalidRequestException("O pedido tem de incluir pelo menos uma jante.");
            }
            Map<WheelType, Integer> result = empty();
            result.put(WheelType.NORMAL, legacyQuantity);
            return result;
        }

        Map<WheelType, Integer> result = empty();
        Set<WheelType> seen = new HashSet<>();
        for (WheelQuantityDto quantity : quantities) {
            if (quantity == null || quantity.type() == null || quantity.quantity() == null) {
                throw new InvalidRequestException("Todas as quantidades de jantes têm de indicar tipo e quantidade.");
            }
            if (!seen.add(quantity.type())) {
                throw new InvalidRequestException("Não é permitido repetir tipos de jantes no mesmo pedido.");
            }
            if (quantity.quantity() < 0) {
                throw new InvalidRequestException("As quantidades de jantes não podem ser negativas.");
            }
            result.put(quantity.type(), quantity.quantity());
        }
        if (total(result) <= 0) {
            throw new InvalidRequestException("O pedido tem de incluir pelo menos uma jante.");
        }
        return result;
    }

    public List<WheelQuantityDto> toDto(Map<WheelType, Integer> quantities) {
        return List.of(
                new WheelQuantityDto(WheelType.BIPARTITE, quantities.getOrDefault(WheelType.BIPARTITE, 0)),
                new WheelQuantityDto(WheelType.WASHED, quantities.getOrDefault(WheelType.WASHED, 0)),
                new WheelQuantityDto(WheelType.NORMAL, quantities.getOrDefault(WheelType.NORMAL, 0))
        );
    }

    public Map<WheelType, Integer> empty() {
        Map<WheelType, Integer> result = new EnumMap<>(WheelType.class);
        for (WheelType type : WheelType.values()) {
            result.put(type, 0);
        }
        return result;
    }

    public int total(Map<WheelType, Integer> quantities) {
        return quantities.values().stream().mapToInt(Integer::intValue).sum();
    }
}
