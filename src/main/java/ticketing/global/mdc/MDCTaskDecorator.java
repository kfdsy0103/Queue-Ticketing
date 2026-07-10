package ticketing.global.mdc;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

@Component
public class MDCTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
