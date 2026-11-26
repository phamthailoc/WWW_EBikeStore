package com.ebike.qa.unit.catalog;

import static org.mockito.Mockito.mock;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.jpa.domain.Specification;

/**
 * Runs a Spring Data {@link Specification} against mocked JPA criteria objects and describes the predicate it builds as text,
 * e.g. {@code and(isTrue(active), greaterThanOrEqualTo(price,100))}. Lets a unit test check a filter without a database.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class SpecRecorder {

    private final Map<Object, String> names = new IdentityHashMap<>();
    final List<String> calls = new ArrayList<>();
    final Root root = mock(Root.class, invocation -> rootAnswer(invocation));
    final CriteriaBuilder builder = mock(CriteriaBuilder.class, invocation -> builderAnswer(invocation));
    final CriteriaQuery query = mock(CriteriaQuery.class);

    String describe(Specification<?> specification) {
        Object predicate = ((Specification) specification).toPredicate(root, query, builder);
        return predicate == null ? "null" : names.getOrDefault(predicate, String.valueOf(predicate));
    }

    /** The raw arguments of the first call of a criteria-builder method, by name. */
    final Map<String, List<Object[]>> rawCalls = new java.util.LinkedHashMap<>();

    private Object rootAnswer(InvocationOnMock invocation) {
        String method = invocation.getMethod().getName();
        if (method.equals("get") && invocation.getArguments().length == 1) {
            return path(String.valueOf((Object) invocation.getArgument(0)));
        }
        if (method.equals("join")) {
            return join(String.valueOf((Object) invocation.getArgument(0)));
        }
        return defaultFor(invocation);
    }

    private Path path(String name) {
        Path path = mock(Path.class, invocation -> {
            if (invocation.getMethod().getName().equals("get") && invocation.getArguments().length == 1) {
                return path(name + "." + invocation.getArgument(0));
            }
            return defaultFor(invocation);
        });
        names.put(path, name);
        return path;
    }

    private Join join(String name) {
        Join join = mock(Join.class, invocation -> {
            if (invocation.getMethod().getName().equals("get") && invocation.getArguments().length == 1) {
                return path(name + "." + invocation.getArgument(0));
            }
            return defaultFor(invocation);
        });
        names.put(join, "join:" + name);
        return join;
    }

    private Object builderAnswer(InvocationOnMock invocation) {
        String method = invocation.getMethod().getName();
        Object[] args = invocation.getArguments();
        rawCalls.computeIfAbsent(method, key -> new ArrayList<>()).add(args);
        String text = method + "(" + java.util.Arrays.stream(args).map(this::text).collect(Collectors.joining(",")) + ")";
        calls.add(text);
        Class<?> type = invocation.getMethod().getReturnType();
        if (!type.isInterface()) {
            return defaultFor(invocation);
        }
        Object result = mock(type);
        names.put(result, text);
        return result;
    }

    private String text(Object argument) {
        if (argument == null) {
            return "null";
        }
        if (argument instanceof Object[] array) {
            return java.util.Arrays.stream(array).map(this::text).collect(Collectors.joining(","));
        }
        return names.getOrDefault(argument, String.valueOf(argument));
    }

    private Object defaultFor(InvocationOnMock invocation) {
        Class<?> type = invocation.getMethod().getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
