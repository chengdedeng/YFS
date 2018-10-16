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
package info.yangguo.yfs.controller;

import info.yangguo.yfs.common.po.FileMetadata;
import info.yangguo.yfs.config.ClusterProperties;
import info.yangguo.yfs.config.YfsConfig;
import info.yangguo.yfs.dto.Result;
import info.yangguo.yfs.dto.ResultCode;
import info.yangguo.yfs.service.MetadataService;
import io.atomix.utils.time.Versioned;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;

@Controller
@RequestMapping(value = "${yfs.group}/admin")
public class AdminController extends BaseController {
    @Autowired
    private ClusterProperties clusterProperties;

    @ApiOperation(value = "node sync full file")
    @RequestMapping(value = "resync/{node}", method = {RequestMethod.PATCH})
    @ResponseBody
    public Result resyncNode(@PathVariable String node) {
        Result result = new Result<>();
        HashSet<String> anomalyFile = new HashSet<>();
        try {
            YfsConfig.fileMetadataMap.values().stream().forEach(fileMetadataVersioned -> {
                long version = fileMetadataVersioned.version();
                FileMetadata fileMetadata = fileMetadataVersioned.value();
                fileMetadata.getAddNodes().remove(node);
                try {
                    if (false == YfsConfig.fileMetadataMap.replace(MetadataService.getKey(fileMetadata), version, fileMetadata)) {
                        anomalyFile.add(MetadataService.getKey(fileMetadata));
                    }
                } catch (Exception e) {
                    anomalyFile.add(MetadataService.getKey(fileMetadata));
                }
            });
            if (anomalyFile.size() == 0) {
                result.setCode(ResultCode.C200.getCode());
            } else {
                result.setCode(ResultCode.C202.getCode());
                result.setValue(anomalyFile);
            }
        } catch (Exception e) {
            result.setCode(ResultCode.C500.getCode());
            result.setValue(ResultCode.C500.getDesc());
        }
        return result;
    }

    @ApiOperation(value = "node sync one file")
    @RequestMapping(value = "resync/{node}/{partition}/{name:.+}", method = {RequestMethod.PUT})
    public void resyncFile(@PathVariable String node, @PathVariable Integer partition, @PathVariable String name, HttpServletResponse response) {
        Result result = new Result<>();
        try {
            String key = MetadataService.getKey(clusterProperties.getGroup(), partition, name);
            Versioned<FileMetadata> versioned = YfsConfig.fileMetadataMap.get(key);
            long version = versioned.version();
            FileMetadata fileMetadata = versioned.value();
            fileMetadata.getAddNodes().remove(node);
            if (false == YfsConfig.fileMetadataMap.replace(key, version, fileMetadata)) {
                result.setCode(ResultCode.C202.getCode());
            } else {
                result.setCode(ResultCode.C200.getCode());
            }
        } catch (Exception e) {
            result.setCode(ResultCode.C500.getCode());
            result.setValue(ResultCode.C500.getDesc());
        }
        outputResult(response, result);
    }
}
