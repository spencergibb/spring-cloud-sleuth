package org.springframework.cloud.sleuth.instrument.web.client;

import static feign.Util.resolveLastTypeParameter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import com.netflix.hystrix.HystrixCommand;
import feign.Contract;
import feign.MethodMetadata;

/**
 * Actually a copy of HystrixDelegatingContract
 */
class TraceCommandDelegatingContract implements Contract {

  private final Contract delegate;

  public TraceCommandDelegatingContract(Contract delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
    List<MethodMetadata> metadatas = this.delegate.parseAndValidatateMetadata(targetType);

    for (MethodMetadata metadata : metadatas) {
      Type type = metadata.returnType();

      if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(HystrixCommand.class)) {
        Type actualType = resolveLastTypeParameter(type, HystrixCommand.class);
        metadata.returnType(actualType);
      }
    }

    return metadatas;
  }
}
