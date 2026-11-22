package com.portability.domain.ports.in;

import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.StartPortabilityCommand;

public interface StartPortabilityUseCase {

    PortabilityOperationResult startPortability(StartPortabilityCommand command);
}
