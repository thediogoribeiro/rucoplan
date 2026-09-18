package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class CustomerNameNormalizer {
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replace('’', '\'')
                .replaceAll("[\\p{Punct}&&[^'-]]+", " ")
                .replace('-', ' ')
                .replace('\'', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public String displayInput(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
