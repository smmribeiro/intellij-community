// "Change return type of called function 'A.plus' to '() -> Int'" "true"
interface A {
    operator fun plus(a: A): String
}

fun foo(a: A): () -> Int {
    return a + a<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix