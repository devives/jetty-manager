package com.devives.jetty.container.contract;

import java.lang.annotation.*;

/**
 * Аннотация указывает, что интерфейс является контрактом для создания прокси-объекта, для вызова методов объекта
 * класс которого принадлежит "чужому" загрузчику классов.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE})
public @interface Contract {

    /**
     * Полное имя класса.
     *
     * @return полное имя класса
     */
    String className();
}
