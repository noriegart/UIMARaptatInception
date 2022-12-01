/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.support.lambda;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.danekja.java.util.function.serializable.SerializableSupplier;

@Deprecated
public class LambdaModel<T>
    extends LoadableDetachableModel<T>
{
    private static final long serialVersionUID = -1455152622735082623L;

    private final SerializableSupplier<T> supplier;
    private boolean autoDetach;

    public LambdaModel(SerializableSupplier<T> aSupplier)
    {
        supplier = aSupplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected T load()
    {
        Object value = supplier.get();
        if (value instanceof IModel) {
            return ((IModel<T>) value).getObject();
        }
        else {
            return (T) value;
        }
    }

    public LambdaModel<T> autoDetaching()
    {
        autoDetach = true;
        return this;
    }

    @Override
    protected void onAttach()
    {
        if (autoDetach) {
            RequestCycle.get().getListeners().add(new IRequestCycleListener()
            {
                @Override
                public void onDetach(RequestCycle aCycle)
                {
                    LambdaModel.this.detach();
                }
            });
        }
    }
}
