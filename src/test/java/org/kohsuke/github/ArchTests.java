package org.kohsuke.github;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kohsuke.github.connector.GitHubConnector;
import org.kohsuke.github.extras.HttpClientGitHubConnector;
import org.kohsuke.github.extras.okhttp3.OkHttpConnector;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.core.domain.JavaClass.namesOf;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameContaining;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.core.domain.properties.HasParameterTypes.Predicates.rawParameterTypes;
import static com.tngtech.archunit.lang.conditions.ArchConditions.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;

public class ArchTests {

    private final static boolean takeSnapshot = System.getProperty("test.github.takeSnapshot", "false") != "false";

    private static final JavaClasses classFiles = getJavaClasses();

    private static JavaClasses getJavaClasses() {
        // if taking a snapshot, set these system properties to allow testApiStability()
        // to refresh known results
        if (takeSnapshot) {
            System.setProperty("archunit.freeze.refreeze", "true");
            System.setProperty("archunit.freeze.store.default.allowStoreUpdate", "true");
        }

        return new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.kohsuke.github");
    }

    private final JavaClasses testClassFiles = new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .withImportOption(new ImportOption.DoNotIncludeJars())
            .importPackages("org.kohsuke.github");

    private final DescribedPredicate<JavaAnnotation<?>> previewAnnotationWithNoMediaType = new DescribedPredicate<JavaAnnotation<?>>(
            "preview has no required media types defined") {

        @Override
        public boolean apply(JavaAnnotation<?> javaAnnotation) {
            boolean isPreview = javaAnnotation.getRawType().isEquivalentTo(Preview.class);
            Object[] values = (Object[]) javaAnnotation.getProperties().get("value");
            return isPreview && values != null && values.length < 1;
        }
    };

    @BeforeClass
    public static void beforeClass() {
        assertThat(classFiles.size(), greaterThan(0));
    }

    @Test
    public void testRequireUseOfAssertThat() {

        final String reason = "This project uses `assertThat(...)` or `assertThrows(...)` instead of other `assert*()` methods.";

        final DescribedPredicate<HasName> assertMethodOtherThanAssertThat = nameContaining("assert")
                .and(DescribedPredicate.not(name("assertThat")).and(DescribedPredicate.not(name("assertThrows"))));

        final ArchRule onlyAssertThatRule = classes()
                .should(not(callMethodWhere(target(assertMethodOtherThanAssertThat))))
                .because(reason);

        onlyAssertThatRule.check(testClassFiles);
    }

    @Test
    public void testApiStability() {
        assertThat("OkHttpConnector must implement HttpConnector",
                Arrays.asList(OkHttpConnector.class.getInterfaces()),
                Matchers.containsInAnyOrder(HttpConnector.class));
    }

    @Test
    public void testPublicSurfaceArea() {
        // This will only be true when running Java 11+
        Assume.assumeThat(GitHubConnector.DEFAULT, instanceOf(HttpClientGitHubConnector.class));

        final ArchRule publicClasses = FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
                .should()
                .bePublic()
                .orShould()
                .beProtected()
                .as("List of public or protected classes should only change intentionally"));

        final ArchRule publicMethods = FreezingArchRule.freeze(ArchRuleDefinition.noMethods()
                .that()
                // in public or protected classes
                .areDeclaredInClassesThat()
                .arePublic()
                .or()
                .areDeclaredInClassesThat()
                .areProtected()
                // verify public or protected methods
                .should()
                .bePublic()
                .orShould()
                .beProtected()
                .as("List of public or protected methods should only change intentionally"));

        final ArchRule publicFields = FreezingArchRule.freeze(ArchRuleDefinition.noFields()
                .that()
                // in public or protected classes
                .areDeclaredInClassesThat()
                .arePublic()
                .or()
                .areDeclaredInClassesThat()
                .areProtected()
                // verify public or protected fields
                .should()
                .bePublic()
                .orShould()
                .beProtected()
                .as("List of public or protected fields should only change intentionally"));

        // These tests should never fail.
        // They are used exclusively to check whether the public surface of the library has changed.
        // All public and protected API surface is logged as violations and saved to files.
        // Any change to those files that is not committed as part of a PR, will be detected by the CI build as a
        // failure.
        publicClasses.check(classFiles);
        publicMethods.check(classFiles);
        publicFields.check(classFiles);
    }

    @Test
    public void testRequireUseOfOnlySpecificApacheCommons() {

        final ArchRule onlyApprovedApacheCommonsMethods = classes()
                .should(notCallMethodsInPackageUnless("org.apache.commons..",
                        // unless it is one of these methods
                        targetMethodIs(StringUtils.class, "capitalize", String.class),
                        targetMethodIs(StringUtils.class, "defaultString", String.class, String.class),
                        targetMethodIs(StringUtils.class, "equals", CharSequence.class, CharSequence.class),
                        targetMethodIs(StringUtils.class, "isBlank", CharSequence.class),
                        targetMethodIs(StringUtils.class, "isEmpty", CharSequence.class),
                        targetMethodIs(StringUtils.class, "join", Iterable.class, String.class),
                        targetMethodIs(StringUtils.class,
                                "prependIfMissing",
                                String.class,
                                CharSequence.class,
                                CharSequence[].class),
                        targetMethodIs(ToStringBuilder.class, "toString"),
                        targetMethodIs(ToStringBuilder.class, "append", String.class, Object.class),
                        targetMethodIs(ToStringBuilder.class, "append", String.class, long.class),
                        targetMethodIs(ToStringBuilder.class, "append", String.class, int.class),
                        targetMethodIs(ToStringBuilder.class, "isEmpty"),
                        targetMethodIs(ToStringBuilder.class, "equals"),
                        targetMethodIs(ToStringBuilder.class, "capitalize"),
                        targetMethodIs(ToStringStyle.class,
                                "append",
                                StringBuffer.class,
                                String.class,
                                Object.class,
                                Boolean.class),
                        targetMethodIs(ReflectionToStringBuilder.class, "accept", Field.class),
                        targetMethodIs(IOUtils.class, "closeQuietly", InputStream.class),
                        targetMethodIs(IOUtils.class, "closeQuietly", Closeable.class),
                        targetMethodIs(IOUtils.class, "copyLarge", InputStream.class, OutputStream.class),
                        targetMethodIs(IOUtils.class, "toString", InputStream.class, Charset.class),
                        targetMethodIs(IOUtils.class, "toString", Reader.class),
                        targetMethodIs(IOUtils.class, "toByteArray", InputStream.class),
                        targetMethodIs(IOUtils.class, "write", byte[].class, OutputStream.class)))
                .because(
                        "Commons methods must be manually verified to be compatible with commons-io:2.4 or earlier and commons-lang3:3.9 or earlier.");

        onlyApprovedApacheCommonsMethods.check(classFiles);
    }

    public static ArchCondition<JavaClass> notCallMethodsInPackageUnless(final String packageIdentifier,
            final DescribedPredicate<JavaCall<?>>... unlessPredicates) {
        DescribedPredicate<JavaCall<?>> restrictedPackageCalls = target(
                HasOwner.Predicates.With.owner(JavaClass.Predicates.resideInAPackage(packageIdentifier)));

        if (unlessPredicates.length > 0) {
            DescribedPredicate<JavaCall<?>> allowed = unlessPredicates[0];
            for (int x = 1; x < unlessPredicates.length; x++) {
                allowed = allowed.or(unlessPredicates[x]);
            }
            restrictedPackageCalls = unless(restrictedPackageCalls, allowed);
        }
        return not(callMethodWhere(restrictedPackageCalls));
    }

    public static DescribedPredicate<JavaCall<?>> targetMethodIs(Class<?> owner,
            String methodName,
            Class<?>... parameterTypes) {
        return JavaCall.Predicates.target(owner(type(owner)))
                .and(JavaCall.Predicates.target(name(methodName)))
                .and(JavaCall.Predicates.target(rawParameterTypes(parameterTypes)))
                .as("method is %s",
                        Formatters.formatMethodSimple(owner.getSimpleName(), methodName, namesOf(parameterTypes)));
    }

    public static <T> DescribedPredicate<T> unless(DescribedPredicate<? super T> first,
            DescribedPredicate<? super T> second) {
        return new UnlessPredicate(first, second);
    }

    private static class UnlessPredicate<T> extends DescribedPredicate<T> {
        private final DescribedPredicate<T> current;
        private final DescribedPredicate<? super T> other;

        UnlessPredicate(DescribedPredicate<T> current, DescribedPredicate<? super T> other) {
            super(current.getDescription() + " unless " + other.getDescription());
            this.current = checkNotNull(current);
            this.other = checkNotNull(other);
        }

        @Override
        public boolean apply(T input) {
            return current.apply(input) && !other.apply(input);
        }
    }
}
