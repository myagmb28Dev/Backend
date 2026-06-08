package com.example.pogun.service.missingpet;

import com.example.pogun.dto.common.ApiResponse.ApiException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AnimalTypeNormalizer {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "dog",
            "cat",
            "rabbit",
            "hamster",
            "guinea_pig",
            "ferret",
            "hedgehog",
            "chinchilla",
            "gerbil",
            "mouse",
            "rat",
            "squirrel",
            "parrot",
            "parakeet",
            "cockatiel",
            "finch",
            "canary",
            "lovebird",
            "chicken",
            "duck",
            "goose",
            "pigeon",
            "turtle",
            "tortoise",
            "lizard",
            "gecko",
            "iguana",
            "chameleon",
            "snake",
            "frog",
            "toad",
            "salamander",
            "fish",
            "goldfish",
            "betta",
            "horse",
            "pony",
            "goat",
            "sheep",
            "pig",
            "cow",
            "alpaca",
            "llama",
            "donkey",
            "monkey",
            "raccoon_dog",
            "fox",
            "deer",
            "meerkat",
            "prairie_dog"
    );

    private static final Map<String, String> ALIASES = buildAliases();

    private AnimalTypeNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("INVALID_ANIMAL_TYPE", "지원하지 않는 animalType 입니다.");
        }
        String normalized = normalizeKey(raw);
        if (SUPPORTED_TYPES.contains(normalized)) {
            return normalized;
        }
        String alias = ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        throw ApiException.badRequest("INVALID_ANIMAL_TYPE", "지원하지 않는 animalType 입니다.");
    }

    public static String normalizeForResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String normalized = normalizeKey(raw);
        if (SUPPORTED_TYPES.contains(normalized)) {
            return normalized;
        }
        return ALIASES.getOrDefault(normalized, raw);
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        register(aliases, "dog", "dogs", "puppy", "canine", "강아지", "개", "견", "멍멍이");
        register(aliases, "cat", "cats", "kitten", "feline", "고양이", "냥이", "묘");
        register(aliases, "rabbit", "bunny", "토끼");
        register(aliases, "hamster", "햄스터");
        register(aliases, "guinea_pig", "guinea pig", "guineapig", "cavy", "기니피그");
        register(aliases, "ferret", "페럿", "흰족제비");
        register(aliases, "hedgehog", "고슴도치");
        register(aliases, "chinchilla", "친칠라");
        register(aliases, "gerbil", "저빌");
        register(aliases, "mouse", "mice", "생쥐", "마우스");
        register(aliases, "rat", "rats", "쥐", "랫드");
        register(aliases, "squirrel", "다람쥐");
        register(aliases, "parrot", "앵무새");
        register(aliases, "parakeet", "budgie", "budgerigar", "잉꼬");
        register(aliases, "cockatiel", "왕관앵무");
        register(aliases, "finch", "핀치");
        register(aliases, "canary", "카나리아");
        register(aliases, "lovebird", "모란앵무");
        register(aliases, "chicken", "hen", "rooster", "닭", "병아리");
        register(aliases, "duck", "오리");
        register(aliases, "goose", "geese", "거위");
        register(aliases, "pigeon", "dove", "비둘기");
        register(aliases, "turtle", "거북이", "수생거북");
        register(aliases, "tortoise", "육지거북");
        register(aliases, "lizard", "도마뱀");
        register(aliases, "gecko", "게코");
        register(aliases, "iguana", "이구아나");
        register(aliases, "chameleon", "카멜레온");
        register(aliases, "snake", "뱀");
        register(aliases, "frog", "개구리");
        register(aliases, "toad", "두꺼비");
        register(aliases, "salamander", "newt", "도롱뇽", "영원");
        register(aliases, "fish", "어류", "물고기");
        register(aliases, "goldfish", "금붕어");
        register(aliases, "betta", "betta fish", "베타");
        register(aliases, "horse", "말");
        register(aliases, "pony", "조랑말");
        register(aliases, "goat", "염소");
        register(aliases, "sheep", "양");
        register(aliases, "pig", "돼지", "미니피그");
        register(aliases, "cow", "cattle", "소");
        register(aliases, "alpaca", "알파카");
        register(aliases, "llama", "라마");
        register(aliases, "donkey", "당나귀");
        register(aliases, "monkey", "원숭이");
        register(aliases, "raccoon_dog", "raccoon dog", "raccoondog", "너구리");
        register(aliases, "fox", "여우");
        register(aliases, "deer", "사슴");
        register(aliases, "meerkat", "미어캣");
        register(aliases, "prairie_dog", "prairie dog", "prairiedog", "프레리도그");
        return Map.copyOf(aliases);
    }

    private static void register(Map<String, String> aliases, String canonical, String... values) {
        aliases.put(normalizeKey(canonical), canonical);
        for (String value : values) {
            aliases.put(normalizeKey(value), canonical);
        }
    }

    private static String normalizeKey(String value) {
        return value == null
                ? null
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
