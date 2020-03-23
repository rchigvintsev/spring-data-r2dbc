/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.r2dbc.repository.query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.relational.repository.query.RelationalParameterAccessor;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper class to allow easy creation of {@link ParameterMetadata}s.
 * <p>
 * This class is an adapted version of {@code org.springframework.data.jpa.repository.query.ParameterMetadataProvider}
 * from Spring Data JPA project.
 *
 * @author Roman Chigvintsev
 */
class ParameterMetadataProvider implements Iterable<ParameterMetadata> {
	private static final Object VALUE_PLACEHOLDER = new Object();

	private final Iterator<? extends Parameter> bindableParameterIterator;
	@Nullable private final Iterator<Object> bindableParameterValueIterator;
	private final List<ParameterMetadata> parameterMetadata = new ArrayList<>();
	private final LikeEscaper likeEscaper;

	/**
	 * Creates new instance of this class with the given {@link RelationalParameterAccessor} and {@link LikeEscaper}.
	 *
	 * @param accessor relational parameter accessor (must not be {@literal null}).
	 * @param likeEscaper escaper for LIKE operator parameters (must not be {@literal null})
	 */
	ParameterMetadataProvider(RelationalParameterAccessor accessor, LikeEscaper likeEscaper) {
		this(accessor.getBindableParameters(), accessor.iterator(), likeEscaper);
	}

	/**
	 * Creates new instance of this class with the given {@link Parameters} and {@link LikeEscaper}.
	 *
	 * @param parameters method parameters (must not be {@literal null})
	 * @param likeEscaper escaper for LIKE operator parameters (must not be {@literal null})
	 */
	ParameterMetadataProvider(Parameters<?, ?> parameters, LikeEscaper likeEscaper) {
		this(parameters, null, likeEscaper);
	}

	/**
	 * Creates new instance of this class with the given {@link Parameters}, {@link Iterator} over all bindable
     * parameter values and {@link LikeEscaper}.
	 *
	 * @param bindableParameterValueIterator iterator over bindable parameter values
	 * @param parameters method parameters (must not be {@literal null})
	 * @param likeEscaper escaper for LIKE operator parameters (must not be {@literal null})
	 */
	private ParameterMetadataProvider(Parameters<?, ?> parameters,
			@Nullable Iterator<Object> bindableParameterValueIterator, LikeEscaper likeEscaper) {
		Assert.notNull(parameters, "Parameters must not be null!");
		Assert.notNull(likeEscaper, "Like escaper must not be null!");

		this.bindableParameterIterator = parameters.getBindableParameters().iterator();
		this.bindableParameterValueIterator = bindableParameterValueIterator;
		this.likeEscaper = likeEscaper;
	}

	@NotNull
	@Override
	public Iterator<ParameterMetadata> iterator() {
		return parameterMetadata.iterator();
	}

	/**
	 * Creates new instance of {@link ParameterMetadata} for the given {@link Part} and next {@link Parameter}.
	 */
	public ParameterMetadata next(Part part) {
		Assert.isTrue(bindableParameterIterator.hasNext(),
				() -> String.format("No parameter available for part %s.", part));
		Parameter parameter = bindableParameterIterator.next();
		String parameterName = getParameterName(parameter, part.getProperty().getSegment());
		Object parameterValue = getParameterValue();
		Part.Type partType = part.getType();

		checkNullIsAllowed(parameterName, parameterValue, partType);
		Class<?> parameterType = parameter.getType();
		Object preparedParameterValue = prepareParameterValue(parameterValue, parameterType, partType);

		ParameterMetadata metadata = new ParameterMetadata(parameterName, preparedParameterValue, parameterType);
		parameterMetadata.add(metadata);
		return metadata;
	}

	private String getParameterName(Parameter parameter, String defaultName) {
		if (parameter.isExplicitlyNamed()) {
			return parameter.getName().orElseThrow(() -> new IllegalArgumentException("Parameter needs to be named"));
		}
		return defaultName;
	}

	@Nullable
	private Object getParameterValue() {
		return bindableParameterValueIterator == null ? VALUE_PLACEHOLDER : bindableParameterValueIterator.next();
	}

	/**
	 * Checks whether {@literal null} is allowed as parameter value.
	 *
	 * @param parameterName parameter name
	 * @param parameterValue parameter value
	 * @param partType method name part type (must not be {@literal null})
	 * @throws IllegalArgumentException if {@literal null} is not allowed as parameter value
	 */
	private void checkNullIsAllowed(String parameterName, @Nullable Object parameterValue, Part.Type partType) {
		if (parameterValue == null && !Part.Type.SIMPLE_PROPERTY.equals(partType)) {
			String message = String.format("Value of parameter with name %s must not be null!", parameterName);
			throw new IllegalArgumentException(message);
		}
	}

	/**
	 * Prepares parameter value before it's actually bound to the query.
	 *
	 * @param value must not be {@literal null}
	 * @return prepared query parameter value
	 */
	@Nullable
	protected Object prepareParameterValue(@Nullable Object value, Class<?> valueType, Part.Type partType) {
		if (value != null && String.class == valueType) {
			switch (partType) {
				case STARTING_WITH:
					return likeEscaper.escape(value.toString()) + "%";
				case ENDING_WITH:
					return "%" + likeEscaper.escape(value.toString());
				case CONTAINING:
				case NOT_CONTAINING:
					return "%" + likeEscaper.escape(value.toString()) + "%";
			}
		}
		return value;
	}
}
