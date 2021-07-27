package annotions;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface BindView {

    int viewId() default 0;
}