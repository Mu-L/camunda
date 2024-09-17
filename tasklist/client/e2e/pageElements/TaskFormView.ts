/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Locator, Page} from '@playwright/test';

class TaskFormView {
  private page: Page;
  readonly nameInput: Locator;
  readonly addressInput: Locator;
  readonly ageInput: Locator;
  readonly numberInput: Locator;
  readonly incrementButton: Locator;
  readonly decrementButton: Locator;
  readonly dateInput: Locator;
  readonly timeInput: Locator;
  readonly checkbox: Locator;
  readonly selectDropdown: Locator;
  readonly tagList: Locator;
  readonly form: Locator;

  constructor(page: Page) {
    this.page = page;
    this.form = page.getByTestId('embedded-form');
    this.nameInput = this.form.getByLabel('Name');
    this.addressInput = this.form.getByLabel('Address*');
    this.ageInput = this.form.getByLabel('Age');
    this.numberInput = this.form.getByLabel('Number');
    this.incrementButton = this.form.getByRole('button', {name: 'Increment'});
    this.decrementButton = this.form.getByRole('button', {name: 'Decrement'});
    this.dateInput = this.form.getByPlaceholder('mm/dd/yyyy');
    this.timeInput = this.form.getByPlaceholder('hh:mm ?m');
    this.checkbox = this.form.getByRole('checkbox');
    this.selectDropdown = this.form.getByText('Select').last();
    this.tagList = this.form.getByPlaceholder('Search');
  }

  async forEachDynamicListItem(
    locator: Locator,
    fn: (value: Locator, index: number, array: Locator[]) => Promise<void>,
  ) {
    const elements = await locator.all();

    for (const element of elements) {
      await fn(element, elements.indexOf(element), elements);
    }
  }
  async fillDatetimeField(name: string, value: string) {
    await this.page.getByRole('textbox', {name}).fill(value);
    await this.page.getByRole('textbox', {name}).press('Enter');
  }

  async selectDropdownValue(value: string) {
    await this.selectDropdown.click();

    let retries = 3;
    while (retries > 0) {
      try {
        await this.page.getByText(value).click({timeout: 40000});
        break;
      } catch (error) {
        console.log(`Retry ${4 - retries} - Element click failed: ${error}`);
        retries--;
      }
    }
  }

  async selectTaglistValues(values: string[]) {
    await this.tagList.click();
    for (const value of values) {
      await this.page.getByText(value, {exact: true}).click();
    }
  }

  async selectDropdownOption(label: string, value: string) {
    await this.page.getByText(label).click();
    await this.page.getByText(value).click();
  }

  async mapDynamicListItems<MappedValue>(
    locator: Locator,
    fn: (
      value: Locator,
      index: number,
      array: Locator[],
    ) => Promise<MappedValue>,
  ): Promise<Array<MappedValue>> {
    const elements = await locator.all();
    const mapped: Array<MappedValue> = [];

    for (const element of elements) {
      mapped.push(await fn(element, elements.indexOf(element), elements));
    }

    return mapped;
  }
  async waitUntilLocatorIsVisible(locator: Locator, page: Page): Promise<void> {
    let elapsedTime = 0;
    const maxWaitTimeSeconds: number = 120000;

    while (elapsedTime < maxWaitTimeSeconds) {
      const element = await locator;

      if (await element.isVisible()) {
        return;
      }

      await page.reload();
      await page.waitForTimeout(10 * 1000);
      // Update the elapsed time
      elapsedTime += 10000;
    }
  }
}
export {TaskFormView};
