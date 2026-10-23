package com.sugamflow.school.formbuilder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds platform forms, including student_master, before the first user request. */
@Component
public class FormDefinitionSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(FormDefinitionSeedRunner.class);

  private final FormDefinitionService forms;

  public FormDefinitionSeedRunner(FormDefinitionService forms) {
    this.forms = forms;
  }

  @Override
  public void run(ApplicationArguments args) {
    forms.ensureDefaults();
    log.info("Platform form definitions are present, including student_master");
  }
}
