/*
 * Copyright 2018-present yangguo@outlook.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.yangguo.yfs.service;

import info.yangguo.yfs.common.po.FileEvent;
import info.yangguo.yfs.config.ClusterProperties;
import info.yangguo.yfs.config.YfsConfig;
import io.atomix.utils.time.Versioned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EventService {
    private static Logger LOGGER = LoggerFactory.getLogger(EventService.class);

    public static boolean create(ClusterProperties clusterProperties, YfsConfig yfsConfig, FileEvent fileEvent, int qos) {
        boolean result = false;
        fileEvent.getAddNodes().add(clusterProperties.getLocal());
        CountDownLatch countDownLatch = new CountDownLatch(qos);
        try {
            yfsConfig.cache.put(fileEvent.getPath(), countDownLatch);
            yfsConfig.fileEventMap.put(fileEvent.getPath(), fileEvent);
            LOGGER.debug("Success to create event of {}", fileEvent.getPath());
            //Preventing network traffic from failing
            countDownLatch.countDown();
            result = countDownLatch.await(clusterProperties.getStore().getQos_max_time(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Qos of {} is fail!", fileEvent.getPath());
        } finally {
            yfsConfig.cache.invalidate(fileEvent.getPath());
        }
        return result;
    }

    public static boolean softDelete(ClusterProperties clusterProperties, YfsConfig yfsConfig, String path) {
        boolean result = false;
        Versioned<FileEvent> tmp = yfsConfig.fileEventMap.get(path);
        if (tmp != null) {
            long version = tmp.version();
            FileEvent fileEvent = tmp.value();
            fileEvent.getRemoveNodes().add(clusterProperties.getLocal());
            result = yfsConfig.fileEventMap.replace(path, version, fileEvent);
        }
        return result;
    }
}
