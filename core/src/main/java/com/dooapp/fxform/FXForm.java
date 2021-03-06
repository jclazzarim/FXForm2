/*
 * Copyright (c) 2013, dooApp <contact@dooapp.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of dooApp nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.dooapp.fxform;

import com.dooapp.fxform.adapter.AdapterProvider;
import com.dooapp.fxform.adapter.DefaultAdapterProvider;
import com.dooapp.fxform.controller.ElementController;
import com.dooapp.fxform.controller.PropertyElementController;
import com.dooapp.fxform.filter.FieldFilter;
import com.dooapp.fxform.filter.FilterException;
import com.dooapp.fxform.filter.NonVisualFilter;
import com.dooapp.fxform.model.*;
import com.dooapp.fxform.reflection.MultipleBeanSource;
import com.dooapp.fxform.reflection.impl.ReflectionFieldProvider;
import com.dooapp.fxform.validation.ClassLevelValidator;
import com.dooapp.fxform.validation.DefaultFXFormValidator;
import com.dooapp.fxform.validation.FXFormValidator;
import com.dooapp.fxform.view.FXFormNode;
import com.dooapp.fxform.view.factory.DefaultFactoryProvider;
import com.dooapp.fxform.view.factory.DefaultLabelFactoryProvider;
import com.dooapp.fxform.view.factory.DefaultTooltipFactoryProvider;
import com.dooapp.fxform.view.factory.FactoryProvider;
import com.dooapp.fxform.view.factory.impl.AutoHidableLabelFactory;
import com.dooapp.fxform.view.factory.impl.DefaultConstraintFactory;
import com.dooapp.fxform.view.factory.impl.LabelFactory;
import com.dooapp.fxform.view.property.DefaultPropertyProvider;
import com.dooapp.fxform.view.property.PropertyProvider;
import com.dooapp.fxform.view.skin.DefaultSkin;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.util.Callback;

import javax.validation.ConstraintViolation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: Antoine Mischler <antoine@dooapp.com>
 * Date: 09/04/11
 * Time: 21:26
 * The FXForm control
 */
public class FXForm<T> extends Control implements FormAPI<T> {

    private final static Logger logger = Logger.getLogger(FXForm.class.getName());

    public static final String LABEL_ID_SUFFIX = "-form-label";

    public static final String LABEL_STYLE = "form-label";

    public static final String EDITOR_ID_SUFFIX = "-form-editor";

    public static final String EDITOR_STYLE = "form-editor";

    public static final String TOOLTIP_ID_SUFFIX = "-form-tooltip";

    public static final String TOOLTIP_STYLE = "form-tooltip";

    public static final String CONSTRAINT_ID_SUFFIX = "-form-constraint";

    public static final String CONSTRAINT_STYLE = "form-constraint";

    public final static String INVALID_STYLE = "-invalid";

    public static final String WARNING_STYLE = "-warning";

    private final ObjectProperty<T> source = new SimpleObjectProperty<T>();

    private StringProperty title = new SimpleStringProperty();

    private final ObservableList<FieldFilter> filters = FXCollections.observableList(new LinkedList<FieldFilter>());

    private final ListProperty<ElementController> controllers = new SimpleListProperty<ElementController>(FXCollections.<ElementController>observableArrayList());

    private final ObjectProperty<ResourceBundle> resourceBundle = new SimpleObjectProperty<ResourceBundle>();

    private final ObjectProperty<FactoryProvider> editorFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<FactoryProvider> tooltipFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<FactoryProvider> labelFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<FactoryProvider> constraintFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<AdapterProvider> adapterProvider = new SimpleObjectProperty<AdapterProvider>();

    private final ObjectProperty<PropertyProvider> propertyProvider = new SimpleObjectProperty<PropertyProvider>();

    private final ObservableList<ConstraintViolation> constraintViolationsList = FXCollections.<ConstraintViolation>observableArrayList();

    private final ObjectProperty<FXFormValidator> fxFormValidator = new SimpleObjectProperty<FXFormValidator>(new DefaultFXFormValidator());

    private final ClassLevelValidator classLevelValidator = new ClassLevelValidator();

    public void setTitle(String title) {
        this.title.set(title);
    }

    public FXForm() {
        this(new DefaultFactoryProvider());
    }

    public FXForm(T source) {
        this(source, new DefaultFactoryProvider());
    }

    public FXForm(FactoryProvider editorFactoryProvider) {
        this(null,
                new FactoryProvider() {
                    public Callback<Void, FXFormNode> getFactory(Element element) {
                        return new LabelFactory();
                    }
                }, new FactoryProvider() {
                    public Callback<Void, FXFormNode> getFactory(Element element) {
                        return new AutoHidableLabelFactory();
                    }
                }, editorFactoryProvider
        );
    }

    public FXForm(T source, FactoryProvider editorFactoryProvider) {
        this(source,
                new DefaultLabelFactoryProvider(),
                new DefaultTooltipFactoryProvider(),
                editorFactoryProvider
        );
    }

    public FXForm(FactoryProvider labelFactoryProvider, FactoryProvider tooltipFactoryProvider, FactoryProvider editorFactoryProvider) {
        this(null, labelFactoryProvider, tooltipFactoryProvider, editorFactoryProvider);
    }

    public FXForm(T source, FactoryProvider labelFactoryProvider, FactoryProvider tooltipFactoryProvider, FactoryProvider editorFactoryProvider) {
        initBundle();
        setPropertyProvider(new DefaultPropertyProvider());
        setAdapterProvider(new DefaultAdapterProvider());
        setEditorFactoryProvider(editorFactoryProvider);
        setLabelFactoryProvider(labelFactoryProvider);
        setTooltipFactoryProvider(tooltipFactoryProvider);
        setConstraintFactoryProvider(new FactoryProvider() {
            public Callback<Void, FXFormNode> getFactory(Element element) {
                return new DefaultConstraintFactory();
            }
        });
        this.source.addListener(new ChangeListener<T>() {
            public void changed(ObservableValue<? extends T> observableValue, T t, T t1) {
                if (t1 == null) {
                    dispose();
                } else if (controllers.isEmpty() || (t1.getClass() != t.getClass())) {
                    try {
                        createControllers();
                    } catch (FormException e) {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
        });
        filters.add(new NonVisualFilter());
        filters.addListener(new ListChangeListener() {
            public void onChanged(Change change) {
                dispose();
                try {
                    createControllers();
                } catch (FormException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        });

        this.setSkin(new DefaultSkin(this));
        classLevelValidator.beanProperty().bind(sourceProperty());
        classLevelValidator.validatorProperty().bind(fxFormValidatorProperty());
        classLevelValidator.constraintViolationsProperty().addListener(new ListChangeListener<ConstraintViolation>() {
            @Override
            public void onChanged(Change<? extends ConstraintViolation> change) {
                while (change.next()) {
                    constraintViolationsList.removeAll(change.getRemoved());
                    constraintViolationsList.addAll(change.getAddedSubList());
                }
            }
        });
        setSource(source);
    }

    protected void dispose() {
        for (ElementController controller : controllers) {
            clearBindings(controller);
            controller.dispose();
        }
        controllers.clear();
    }


    private void clearBindings(ElementController controller) {
        source.unbind();
    }

    private void createControllers() throws FormException {
        if (source.get() == null)
            return;
        controllers.clear();
        List<Field> fields = new ReflectionFieldProvider().getProperties(source.get());
        List<Element> elements = new LinkedList<Element>();
        ElementFactory elementFactory = new DefaultElementFactory();
        for (Field field : fields) {
            final Element element = elementFactory.create(field);
            if (element != null) {
                element.sourceProperty().bind(new ObjectBinding() {
                    {
                        bind(source);
                    }

                    @Override
                    protected Object computeValue() {
                        if (source.get() != null && source.get() instanceof MultipleBeanSource) {
                            MultipleBeanSource multipleBeanSource = (MultipleBeanSource) source.get();
                            return multipleBeanSource.getSource(element);
                        }
                        return source.get();
                    }
                });
                // if something went wrong and we are not able to get element type, ignore it
                if (element.getType() != null) {
                    elements.add(element);
                }
            }
        }
        for (FieldFilter filter : filters) {
            try {
                elements = filter.filter(elements);
            } catch (FilterException e) {
                throw new FormException("Something went wrong happened while applying " + filter + ":\n" + e.getMessage(), e);
            }
        }
        for (Element element : elements) {
            ElementController controller = null;
            if (PropertyElement.class.isAssignableFrom(element.getClass())) {
                controller = createPropertyElementController((PropertyElement) element);
            } else {
                controller = new ElementController(this, element);
            }
            if (controller != null) {
                controllers.add(controller);
            }
        }
    }

    protected ElementController createPropertyElementController(PropertyElement element) {
        return new PropertyElementController(this, element);
    }

    /**
     * Auto loading of default resource bundle and css file.
     */
    private void initBundle() {
        final StackTraceElement element = getCallingClass();
        String bundle = element.getClassName();
        if (resourceBundle.get() == null) {
            try {
                resourceBundle.set(ResourceBundle.getBundle(bundle));
            } catch (MissingResourceException e) {
                // no default resource bundle found
            }
        }
        sceneProperty().addListener(new ChangeListener<Scene>() {
            public void changed(ObservableValue<? extends Scene> observableValue, Scene scene, Scene scene1) {
                String path = element.getClassName().replaceAll("\\.", "/") + ".css";
                URL css = FXForm.class.getClassLoader().getResource(path);
                if (css != null && observableValue.getValue() != null) {
                    getScene().getStylesheets().add(css.toExternalForm());
                }
            }
        });
    }

    /**
     * Retrieve the calling class in which the form is being created.
     *
     * @return the StackTraceElement representing the calling class
     */
    private StackTraceElement getCallingClass() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int i = 1;
        while (stackTrace[i].getClassName().equals(getClass().getName())) {
            i++;
        }
        return stackTrace[i];
    }

    public StringProperty titleProperty() {
        return title;
    }

    public ObservableList<ElementController> getControllers() {
        return controllers;
    }

    public T getSource() {
        return source.get();
    }

    public void setSource(T source) {
        this.source.set(source);
    }

    public ObjectProperty<T> sourceProperty() {
        return source;
    }

    public ObservableList<FieldFilter> getFilters() {
        return filters;
    }

    public void addFilters(FieldFilter... filters) {
        this.filters.addAll(filters);
    }

    /**
     * Set the resource bundle used by this form to i18n labels, tooltips,...
     *
     * @param resourceBundle
     */
    public void setResourceBundle(ResourceBundle resourceBundle) {
        this.resourceBundle.set(resourceBundle);
    }

    public ObjectProperty<ResourceBundle> resourceBundleProperty() {
        return resourceBundle;
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle.get();
    }

    public FactoryProvider getEditorFactoryProvider() {
        return editorFactoryProvider.get();
    }

    public FactoryProvider getTooltipFactoryProvider() {
        return tooltipFactoryProvider.get();
    }

    public FactoryProvider getLabelFactoryProvider() {
        return labelFactoryProvider.get();
    }

    public FactoryProvider getConstraintFactoryProvider() {
        return constraintFactoryProvider.get();
    }

    public void setEditorFactoryProvider(FactoryProvider editorFactoryProvider1) {
        editorFactoryProvider.set(editorFactoryProvider1);
    }

    public void setLabelFactoryProvider(FactoryProvider labelFactoryProvider1) {
        labelFactoryProvider.set(labelFactoryProvider1);
    }

    public void setTooltipFactoryProvider(FactoryProvider tooltipFactoryProvider1) {
        tooltipFactoryProvider.set(tooltipFactoryProvider1);
    }

    public void setConstraintFactoryProvider(FactoryProvider constraintFactoryProvider1) {
        constraintFactoryProvider.set(constraintFactoryProvider1);
    }

    public ObjectProperty<FactoryProvider> editorFactoryProvider() {
        return editorFactoryProvider;
    }

    public ObjectProperty<FactoryProvider> labelFactoryProvider() {
        return labelFactoryProvider;
    }

    public ObjectProperty<FactoryProvider> tooltipFactoryProvider() {
        return tooltipFactoryProvider;
    }

    public ObjectProperty<FactoryProvider> constraintFactoryProvider() {
        return constraintFactoryProvider;
    }

    public AdapterProvider getAdapterProvider() {
        return adapterProvider.get();
    }

    public void setAdapterProvider(AdapterProvider adapterProvider1) {
        this.adapterProvider.set(adapterProvider1);
    }

    public ObjectProperty<AdapterProvider> adapterProviderProperty() {
        return adapterProvider;
    }

    public PropertyProvider getPropertyProvider() {
        return propertyProvider.get();
    }

    public void setPropertyProvider(PropertyProvider propertyProvider) {
        this.propertyProvider.set(propertyProvider);
    }

    public ObjectProperty<PropertyProvider> propertyProviderProperty() {
        return propertyProvider;
    }

    public FXFormValidator getFxFormValidator() {
        return fxFormValidator.get();
    }

    public void setFxFormValidator(FXFormValidator fxFormValidator) {
        this.fxFormValidator.set(fxFormValidator);
    }

    public ObjectProperty<FXFormValidator> fxFormValidatorProperty() {
        return fxFormValidator;
    }

    /**
     * Get an ObservableList mirroring all constraint violations in the form.
     * This method can be used to implement some kind of validation of the form or
     * to display all constraint violations.
     * This list is updated each time the user inputs data that violates a constraint or fixes a violation.
     *
     * @return the ObservableList containing current constraint violations
     */
    public ObservableList<ConstraintViolation> getConstraintViolations() {
        return constraintViolationsList;
    }

    public ClassLevelValidator getClassLevelValidator() {
        return classLevelValidator;
    }
}
