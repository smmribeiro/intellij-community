// "Add use-site target 'param'" "true"
// ACTION "Change use-site target to 'field'"
// ACTION "Change use-site target to 'property'"
// COMPILER_ARGUMENTS: -XXLanguage:+AnnotationDefaultTargetMigrationWarning
// IGNORE_K1

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Anno

class MyClass(<caret>@Anno val foo: String)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$AddAnnotationUseSiteTargetFix