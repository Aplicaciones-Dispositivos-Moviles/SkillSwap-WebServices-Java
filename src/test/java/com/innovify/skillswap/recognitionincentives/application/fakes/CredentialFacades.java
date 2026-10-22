package com.innovify.skillswap.recognitionincentives.application.fakes;

import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.SkillCatalogContextFacade;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The facades of the other contexts that the credentials need, with only the methods they call. */
public final class CredentialFacades {

    private CredentialFacades() {
    }

    /** Skills a student completed, as "userId:skillTag". */
    public static final class LearningPaths {
        private final Set<String> completed = new HashSet<>();
        private int calls;

        public LearningPaths completed(int userId, String skillTag) {
            completed.add(userId + ":" + skillTag);
            return this;
        }

        public int calls() {
            return calls;
        }

        public LearningPathContextFacade facade() {
            return proxy(LearningPathContextFacade.class, (method, args) -> {
                if (!method.equals("hasCompletedSkill")) {
                    throw new UnsupportedOperationException(method);
                }
                calls++;
                return completed.contains(args[0] + ":" + args[1]);
            });
        }
    }

    /** The names of the skills; a tag that is not here is unknown. */
    public static final class SkillCatalog {
        private final Map<String, String> names = new HashMap<>();

        public SkillCatalog named(String skillTag, String name) {
            names.put(skillTag, name);
            return this;
        }

        public SkillCatalogContextFacade facade() {
            return proxy(SkillCatalogContextFacade.class, (method, args) -> {
                Map<String, String> found = new HashMap<>();
                for (Object tag : (Collection<?>) args[0]) {
                    if (names.containsKey(tag)) {
                        found.put((String) tag, names.get(tag));
                    }
                }
                return found;
            });
        }
    }

    /** The names students registered. */
    public static final class Iam {
        private final Map<Integer, String> names = new HashMap<>();

        public Iam named(int userId, String fullName) {
            names.put(userId, fullName);
            return this;
        }

        public IamContextFacade facade() {
            return proxy(IamContextFacade.class, (method, args) ->
                    Optional.ofNullable(names.get((Integer) args[0])));
        }
    }

    private interface Handler {
        Object handle(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.handle(method.getName(), args == null ? new Object[0] : args));
    }
}
