/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import faker from 'faker';

const { system, random, lorem, internet } = faker;
const { fileName: name } = system;
const { uuid: id } = random;
const { paragraph: message } = lorem;
const { domainName, ip, userName } = internet;

const topics = () => {
  return [
    {
      name: name(),
      id: id(),
    },
    {
      name: name(),
      id: id(),
    },
  ];
};

export { name, id, message, topics, domainName, ip, userName };
