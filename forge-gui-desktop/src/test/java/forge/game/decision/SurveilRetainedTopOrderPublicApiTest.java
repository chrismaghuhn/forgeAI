package forge.game.decision;

import forge.game.card.Card;
import forge.game.card.CardView;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SurveilRetainedTopOrderPublicApiTest {
    @Test
    public void exposesOnlyTheExactRetainedTopOrderEnums() {
        assertEquals(SurveilPartitionOwner.values(),
                new SurveilPartitionOwner[] {
                        SurveilPartitionOwner.NATIVE, SurveilPartitionOwner.EXTERNAL
                });
        assertEquals(SurveilRetainedTopOrderProfile.values(),
                new SurveilRetainedTopOrderProfile[] {
                        SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER
                });
        assertEquals(SurveilRetainedTopOrderDirection.values(),
                new SurveilRetainedTopOrderDirection[] {
                        SurveilRetainedTopOrderDirection.TOP_FIRST
                });
        assertEquals(SurveilRetainedTopOrderCandidateKind.values(),
                new SurveilRetainedTopOrderCandidateKind[] {
                        SurveilRetainedTopOrderCandidateKind.SELECT_NEXT_TOP
                });
        assertEquals(OrderDirection.values(), new OrderDirection[] {OrderDirection.RESOLVE_FIRST});
    }

    @Test
    public void contextContainsExactlyTheApprovedReadOnlyGettersAndFields() {
        assertPublicMethodNames(SurveilRetainedTopOrderContext.class,
                Set.of("getProfile", "getDirection", "getSurveilSessionId",
                        "getDecisionStepIndex", "getChoosingPlayerId", "getRetainedItemCount",
                        "getRetainedItems"));
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getProfile",
                SurveilRetainedTopOrderProfile.class);
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getDirection",
                SurveilRetainedTopOrderDirection.class);
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getSurveilSessionId", long.class);
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getDecisionStepIndex", int.class);
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getChoosingPlayerId", int.class);
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getRetainedItemCount", int.class);
        assertPublicMethod(SurveilRetainedTopOrderContext.class, "getRetainedItems", List.class);
        assertNoPublicFields(SurveilRetainedTopOrderContext.class);
        assertDeclaredFields(SurveilRetainedTopOrderContext.class,
                Map.of("profile", SurveilRetainedTopOrderProfile.class,
                        "direction", SurveilRetainedTopOrderDirection.class,
                        "surveilSessionId", long.class,
                        "decisionStepIndex", int.class,
                        "choosingPlayerId", int.class,
                        "retainedItemCount", int.class,
                        "retainedItems", List.class));
    }

    @Test
    public void contextConstructorIsPackagePrivateAndFieldsArePrivateFinal() {
        final Constructor<?> constructor;
        try {
            constructor = SurveilRetainedTopOrderContext.class.getDeclaredConstructor(
                    SurveilRetainedTopOrderProfile.class, SurveilRetainedTopOrderDirection.class,
                    long.class, int.class, int.class, int.class, List.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }

        assertEquals(constructor.getParameterCount(), 7);
        assertEquals(Arrays.asList(constructor.getParameterTypes()),
                Arrays.asList(SurveilRetainedTopOrderProfile.class,
                        SurveilRetainedTopOrderDirection.class, long.class, int.class,
                        int.class, int.class, List.class));
        assertEquals(constructor.getModifiers()
                & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE), 0);

        for (final Field field : SurveilRetainedTopOrderContext.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()),
                    field.getName() + " must be private");
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    field.getName() + " must be final");
        }
    }

    @Test
    public void retainedItemsUsesTheExactPublicGenericProjection() {
        try {
            final Field field = SurveilRetainedTopOrderContext.class.getDeclaredField("retainedItems");
            assertExactSurveilPartitionCardList(field.getGenericType());

            final Method getter = SurveilRetainedTopOrderContext.class
                    .getDeclaredMethod("getRetainedItems");
            assertExactSurveilPartitionCardList(getter.getGenericReturnType());
        } catch (NoSuchFieldException | NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    public void publicPayloadContainsNoEngineIdentityOrOrderingState() {
        for (final Class<?> type : List.of(
                SurveilRetainedTopOrderContext.class, SurveilPartitionCard.class)) {
            for (final Field field : type.getDeclaredFields()) {
                assertFalse(hasForbiddenName(field.getName()),
                        type.getSimpleName() + " exposes forbidden field " + field.getName());
                assertFalse(hasForbiddenPayloadType(field.getType()),
                        type.getSimpleName() + " exposes forbidden field type " + field.getType());
                assertFalse(field.getGenericType().getTypeName().contains("forge.game.card.Card"),
                        type.getSimpleName() + " exposes a card payload type");
            }
            for (final Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertFalse(hasForbiddenName(method.getName()),
                        type.getSimpleName() + " exposes forbidden method " + method.getName());
                assertFalse(hasForbiddenPayloadType(method.getReturnType()),
                        type.getSimpleName() + " exposes forbidden return type " + method.getReturnType());
                for (final Class<?> parameterType : method.getParameterTypes()) {
                    assertFalse(hasForbiddenPayloadType(parameterType),
                            type.getSimpleName() + " accepts forbidden parameter type " + parameterType);
                }
            }
        }
    }

    @Test
    public void duplicateLookingRetainedProjectionsRemainDistinctByItemId() {
        final SurveilPartitionCard first = card(1L, "Island");
        final SurveilPartitionCard second = card(2L, "Island");
        final SurveilRetainedTopOrderContext context = context(List.of(first, second));

        assertEquals(context.getRetainedItems().stream()
                .map(SurveilPartitionCard::getVisibleName).toList(), List.of("Island", "Island"));
        assertEquals(context.getRetainedItems().stream()
                .map(SurveilPartitionCard::getItemId).toList(), List.of(1L, 2L));
        assertNotEquals(first.getItemId(), second.getItemId());
    }

    @Test
    public void contextExposesStableMetadataAndCopiesRetainedProjection() {
        final List<SurveilPartitionCard> source = new ArrayList<>(retainedItems());
        final SurveilRetainedTopOrderContext context = new SurveilRetainedTopOrderContext(
                SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, 1, 11, 2, source);

        source.clear();
        assertSame(context.getProfile(), SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER);
        assertSame(context.getDirection(), SurveilRetainedTopOrderDirection.TOP_FIRST);
        assertEquals(context.getSurveilSessionId(), 7L);
        assertEquals(context.getDecisionStepIndex(), 1);
        assertEquals(context.getChoosingPlayerId(), 11);
        assertEquals(context.getRetainedItemCount(), 2);
        assertEquals(context.getRetainedItems().size(), 2);
        expectThrows(UnsupportedOperationException.class,
                () -> context.getRetainedItems().add(card(3L, "Mountain")));
    }

    @Test
    public void contextRejectsNullProfileDirectionListAndProjectionEntries() {
        expectThrows(NullPointerException.class,
                () -> new SurveilRetainedTopOrderContext(null,
                        SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, 0, 11, 1,
                        List.of(card(1L, "Island"))));
        expectThrows(NullPointerException.class,
                () -> new SurveilRetainedTopOrderContext(
                        SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER, null,
                        7L, 0, 11, 1, List.of(card(1L, "Island"))));
        expectThrows(NullPointerException.class,
                () -> new SurveilRetainedTopOrderContext(
                        SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                        SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, 0, 11, 0, null));
        expectThrows(NullPointerException.class,
                () -> new SurveilRetainedTopOrderContext(
                        SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                        SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, 0, 11, 2,
                        Arrays.asList(card(1L, "Island"), null)));
    }

    @Test
    public void contextRejectsNegativeStepAndRetainedCardinality() {
        expectThrows(IllegalArgumentException.class,
                () -> new SurveilRetainedTopOrderContext(
                        SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                        SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, -1, 11, 2,
                        retainedItems()));
        expectThrows(IllegalArgumentException.class,
                () -> new SurveilRetainedTopOrderContext(
                        SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                        SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, 0, 11, -1,
                        List.of()));
    }

    private static SurveilRetainedTopOrderContext context(
            final List<SurveilPartitionCard> retainedItems) {
        return new SurveilRetainedTopOrderContext(
                SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                SurveilRetainedTopOrderDirection.TOP_FIRST, 7L, 0, 11,
                retainedItems.size(), retainedItems);
    }

    private static List<SurveilPartitionCard> retainedItems() {
        return List.of(card(1L, "Island"), card(2L, "Forest"));
    }

    private static SurveilPartitionCard card(final long itemId, final String visibleName) {
        return new SurveilPartitionCard(itemId, visibleName);
    }

    private static boolean hasForbiddenName(final String name) {
        final String normalized = name.toLowerCase();
        return normalized.contains("card")
                || normalized.contains("gametimestamp")
                || normalized.contains("snapshot")
                || normalized.contains("ordinal")
                || normalized.contains("library")
                || normalized.contains("zone")
                || normalized.contains("pair")
                || normalized.contains("native")
                || normalized.contains("graveyard")
                || containsNameToken(name, "ai")
                || normalized.contains("rng")
                || normalized.contains("random");
    }

    private static boolean containsNameToken(final String name, final String token) {
        final String separated = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return separated.equals(token) || separated.startsWith(token + "_")
                || separated.endsWith("_" + token) || separated.contains("_" + token + "_");
    }

    private static boolean hasForbiddenPayloadType(final Class<?> type) {
        final String simpleName = type.getSimpleName();
        return Card.class.isAssignableFrom(type)
                || CardView.class.isAssignableFrom(type)
                || simpleName.equals("CardLKI")
                || simpleName.equals("Pair")
                || simpleName.equals("Game")
                || simpleName.equals("Player")
                || simpleName.equals("SpellAbility")
                || simpleName.equals("ZoneType")
                || java.util.Random.class.isAssignableFrom(type);
    }

    private static void assertExactSurveilPartitionCardList(final Type genericType) {
        assertTrue(genericType instanceof ParameterizedType);
        final ParameterizedType parameterizedType = (ParameterizedType) genericType;
        assertEquals(parameterizedType.getRawType(), List.class);
        assertEquals(Arrays.asList(parameterizedType.getActualTypeArguments()),
                List.of(SurveilPartitionCard.class));
    }

    private static void assertPublicMethodNames(final Class<?> type,
            final Set<String> expectedNames) {
        final Method[] methods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);
        assertEquals(methods.length, expectedNames.size());
        assertEquals(Arrays.stream(methods).map(Method::getName).collect(Collectors.toSet()),
                expectedNames);
    }

    private static void assertPublicMethod(final Class<?> type, final String name,
            final Class<?> returnType, final Class<?>... parameterTypes) {
        try {
            final Method method = type.getDeclaredMethod(name, parameterTypes);
            assertTruePublic(method);
            assertEquals(method.getReturnType(), returnType);
            assertEquals(Arrays.asList(method.getParameterTypes()), Arrays.asList(parameterTypes));
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertTruePublic(final Method method) {
        assertEquals(Modifier.isPublic(method.getModifiers()), true);
        assertEquals(Modifier.isStatic(method.getModifiers()), false);
    }

    private static void assertNoPublicFields(final Class<?> type) {
        assertEquals(type.getFields().length, 0);
        assertEquals(Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())).count(), 0L);
    }

    private static void assertDeclaredFields(final Class<?> type,
            final Map<String, Class<?>> expectedFields) {
        final Field[] fields = type.getDeclaredFields();
        assertEquals(fields.length, expectedFields.size());
        assertEquals(Arrays.stream(fields).map(Field::getName).collect(Collectors.toSet()),
                expectedFields.keySet());
        for (final Map.Entry<String, Class<?>> expected : expectedFields.entrySet()) {
            try {
                assertEquals(type.getDeclaredField(expected.getKey()).getType(), expected.getValue());
            } catch (NoSuchFieldException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
