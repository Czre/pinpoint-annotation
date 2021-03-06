/*
 * Copyright 2014 NAVER Corp.
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
package com.navercorp.pinpoint.plugin.redis;

/**
 * 命令上下文
 * @author jaehong.kim
 *
 */
public class CommandContext {
    private long readBeginTime; // 读取开始时间
    private long readEndTime; // 读取结束时间
    private boolean readFail; // 读取失败
    
    private long writeBeginTime; // 写入开始时间
    private long writeEndTime; // 写入结束时间
    private boolean writeFail; // 写入失败

    public void setReadBeginTime(long readBeginTime) {
        this.readBeginTime = readBeginTime;
    }

    public void setReadEndTime(long readEndTime) {
        this.readEndTime = readEndTime;
    }
    
    public boolean isReadFail() {
        return readFail;
    }

    public void setReadFail(boolean readFail) {
        this.readFail = readFail;
    }

    public void setWriteBeginTime(long writeBeginTime) {
        this.writeBeginTime = writeBeginTime;
    }

    public void setWriteEndTime(long writeEndTime) {
        this.writeEndTime = writeEndTime;
    }
    
    public boolean isWriteFail() {
        return writeFail;
    }

    public void setWriteFail(boolean writeFail) {
        this.writeFail = writeFail;
    }

    public long getWriteElapsedTime() {
        long result = writeEndTime - writeBeginTime;
        return result > 0 ? result : 0;
    }

    public long getReadElapsedTime() {
        long result = readEndTime - readBeginTime;
        return result > 0 ? result : 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{readBeginTime=");
        builder.append(readBeginTime);
        builder.append(", readEndTime=");
        builder.append(readEndTime);
        builder.append(", readFail=");
        builder.append(readFail);
        builder.append(", writeBeginTime=");
        builder.append(writeBeginTime);
        builder.append(", writeEndTime=");
        builder.append(writeEndTime);
        builder.append(", writeFail=");
        builder.append(writeFail);
        builder.append("}");
        return builder.toString();
    }
}