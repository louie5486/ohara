#
# Copyright 2019 is-land
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM oharastream/ohara:deps

# add user
ARG USER=ohara
RUN groupadd $USER
RUN useradd -ms /bin/bash -g $USER $USER

# copy gradle dependencies
RUN cp -r /root/.gradle /home/$USER/
RUN chown -R $USER:$USER /home/$USER/.gradle

# clone database instance
RUN cp -r /root/.embedmysql /home/$USER/
RUN chown -R $USER:$USER /home/$USER/.embedmysql

# change to user
USER $USER
WORKDIR /home/$USER