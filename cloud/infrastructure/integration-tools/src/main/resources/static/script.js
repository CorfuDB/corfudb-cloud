'use strict';

const noPadding = {
    paddingRight: 0, paddingLeft: 0
}

function Welcome(props) {
    const {register, control, handleSubmit} = ReactHookForm.useForm({
        defaultValues: {
            test: [{name: "server1", url: "http://example.com/archive.tgz"}]
        }
    });
    const {fields, append, prepend, remove, swap, move, insert} = ReactHookForm.useFieldArray(
        {
            control,
            name: "test"
        }
    );

    const onSubmit = data => {
        console.log("form data: " + JSON.stringify(data))

        const requestOptions = {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(data)
        };

        fetch('/processing', requestOptions)
        let redirectionFunction = () => {
            window.location.href = "/processing/" + data.aggregationUnit
        };
        setTimeout(redirectionFunction, 100000)
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)}>
            <div className="row">
                <div className="col-lg-9">
                    <div className="input-group">
                        <span className="input-group-addon">Aggregation unit: </span>
                        <input className="form-control" name={"aggregationUnit"} ref={register()}/>
                    </div>
                </div>
            </div>

            <br/>
            <br/>

            <p className="lead">Data archives</p>

            <div className="container">
                {fields.map((item, index) => {
                    return (
                        <div className="row" key={item.id}>
                            <div className="col-lg-2" style={noPadding}>
                                <label>name</label>
                                <input
                                    className="form-control"
                                    name={`archives[${index}].name`}
                                    defaultValue={`${item.name}`}
                                    ref={register()}
                                />
                            </div>

                            <div className="col-lg-9">
                                <label>url</label>
                                <ReactHookForm.Controller
                                    className="form-control"
                                    as={<input/>}
                                    name={`archives[${index}].url`}
                                    size={120}
                                    control={control}
                                    defaultValue={item.url} // make sure to set up defaultValue
                                />
                            </div>

                            <div className="col-lg-1">
                                <label>action</label>
                                <button className="btn btn-warning" type="submit" disabled
                                        onClick={() => remove(index)}>Delete
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>

            <br/>

            <div className="row">
                <div className="col-lg-2">
                    <section>
                        <button className="btn btn-default" type="button" onClick={() => {
                            append({name: "server", url: "http://example.com/arhive.tgz"});
                        }}>Add new archive
                        </button>
                    </section>
                </div>
            </div>
            <br/>
            <br/>

            <div className="row">
                <div className="col-lg-12">
                    <button className="btn btn-success" type="submit">Start processing</button>
                </div>
            </div>
        </form>
    );
}

ReactDOM.render(
    <Welcome/>,
    document.getElementById('root')
);