package com.example.commonlib.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class AppLocaleTest {

  @Test
  void constantsAreCorrect() {
    assertThat(AppLocale.LOCALE_EN_US).isEqualTo("en-US");
    assertThat(AppLocale.LOCALE_FA).isEqualTo("fa");
    assertThat(AppLocale.SUPPORTED_LOCALES).containsExactlyInAnyOrder("en-US", "fa");
    assertThat(AppLocale.DEFAULT_LOCALE).isEqualTo("en-US");
  }

  @Test
  void privateConstructorThrows() throws Exception {
    Constructor<AppLocale> ctor = AppLocale.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    assertThatThrownBy(ctor::newInstance)
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
